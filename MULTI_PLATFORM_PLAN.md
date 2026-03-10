# Multi-Platform Chat Ingestion — Implementation Plan

## Status

| Phase | Status | Notes |
|-------|--------|-------|
| **0** | **COMPLETE** | Schema rename, JOOQ updates, DTOs, PlatformService, API/frontend renames — 98 files changed, all modules compile |
| **1** | Not started | YouTube producer |
| **2** | Not started | Kick producer |
| **3** | Not started | Rumble producer |
| **4** | Not started | Cross-platform features |

---

## Phase 0: Schema Rename & Platform Abstraction (COMPLETE)

All work completed. See git diff for full changeset. Key deliverables:

- Migration `0022-platform-abstraction.sql` — schema `twitch` → `chat`, columns `twitch_id` → `channel_id`, `platform` discriminator, identity tables
- JOOQ generated code updated across all tables, pojos, records, keys, indexes
- `ChatMessage` record, `PlatformService` interface + `TwitchPlatformService` adapter
- All 5 API controllers, 8+ services, 8 wrapper services, 11 repository impls, frontend renamed
- `SessionWithUser` and `LiveMetrics` updated with `platform` field

---

## Phase 1: YouTube Producer

**Goal:** Add YouTube Live Chat ingestion as the first non-Twitch platform.

### 1A. New Library: `chatalytics-youtube-library/lib`

**Dependencies:** `com.google.apis:google-api-services-youtube:v3-rev20240514-2.0.0`

```java
// YouTubeApiClient.java
@Service
public class YouTubeApiClient {

    private final YouTube youtube;

    public YouTubeApiClient(@Value("${youtube.api-key}") String apiKey) {
        this.youtube = new YouTube.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                request -> {})
            .setApplicationName("chatalytics")
            .build();
    }

    public List<LiveChatMessage> pollChatMessages(String liveChatId, String pageToken) {
        YouTube.LiveChatMessages.List request = youtube.liveChatMessages()
            .list(liveChatId, List.of("snippet", "authorDetails"));
        if (pageToken != null) request.setPageToken(pageToken);
        LiveChatMessageListResponse response = request.execute();
        // Store response.getPollingIntervalMillis() for adaptive polling
        return response.getItems();
    }

    public String findLiveChatId(String channelId) {
        // Search for active live broadcast by channel
        SearchListResponse response = youtube.search().list(List.of("id"))
            .setChannelId(channelId)
            .setEventType("live")
            .setType(List.of("video"))
            .execute();
        if (response.getItems().isEmpty()) return null;
        String videoId = response.getItems().get(0).getId().getVideoId();

        // Get liveChatId from the video
        VideoListResponse videoResponse = youtube.videos()
            .list(List.of("liveStreamingDetails"))
            .setId(List.of(videoId))
            .execute();
        return videoResponse.getItems().get(0)
            .getLiveStreamingDetails().getActiveLiveChatId();
    }

    public Optional<Channel> findChannelByIdentifier(String identifier) {
        // Try by ID first, then by custom URL/handle
        ChannelListResponse response = youtube.channels()
            .list(List.of("snippet", "statistics"))
            .setId(List.of(identifier))
            .execute();
        if (response.getItems().isEmpty()) {
            response = youtube.channels()
                .list(List.of("snippet", "statistics"))
                .setForHandle(identifier)
                .execute();
        }
        return response.getItems().stream().findFirst();
    }
}
```

```java
// YouTubePlatformService.java
@Service
@RequiredArgsConstructor
public class YouTubePlatformService implements PlatformService {

    private final YouTubeApiClient client;

    @Override
    public String platform() { return "youtube"; }

    @Override
    public Set<PlatformStreamInfo> findOnlineStreams(List<PlatformUser> users) {
        // For each user, check if they have an active live broadcast
        // YouTube API doesn't have a batch "check online" endpoint like Twitch
        // Must check each channel individually — rate limit accordingly
        return users.stream()
            .map(u -> {
                String liveChatId = client.findLiveChatId(u.id());
                if (liveChatId == null) return null;
                return new PlatformStreamInfo(u.id(), u.login(), null, null, 0);
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    @Override
    public Optional<PlatformUser> findUserByIdentifier(String identifier) {
        return client.findChannelByIdentifier(identifier)
            .map(ch -> new PlatformUser(
                ch.getId(),
                ch.getSnippet().getCustomUrl(),
                ch.getSnippet().getTitle(),
                "youtube"));
    }
}
```

**Files to create:**
- `chatalytics-youtube-library/lib/build.gradle`
- `chatalytics-youtube-library/lib/src/main/java/space/forloop/chatalytics/youtube/YouTubeApiClient.java`
- `chatalytics-youtube-library/lib/src/main/java/space/forloop/chatalytics/youtube/YouTubePlatformService.java`
- `chatalytics-youtube-library/lib/src/main/java/space/forloop/chatalytics/youtube/YouTubeQuotaTracker.java`

**build.gradle:**
```groovy
plugins {
    id 'java-library'
    id 'io.spring.dependency-management'
}

dependencies {
    api project(':chatalytics-data-library')
    api 'com.google.apis:google-api-services-youtube:v3-rev20240514-2.0.0'
    api 'com.google.auth:google-auth-library-oauth2-http:1.23.0'

    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
}
```

### 1B. New Service: `chatalytics-youtube-producer`

Spring Boot service that polls YouTube Live Chat for active sessions.

```java
// YouTubeChatPoller.java
@Slf4j
@Service
@RequiredArgsConstructor
public class YouTubeChatPoller {

    private final YouTubeApiClient youtubeClient;
    private final KafkaProducerService kafkaProducer;

    // channelId -> polling state
    private final ConcurrentHashMap<String, PollState> activePollers = new ConcurrentHashMap<>();

    @KafkaListener(topics = "raw-sessions-online")
    public void onSessionOnline(SessionWithUser session) {
        if (!"youtube".equals(session.platform())) return;

        String channelId = String.valueOf(session.channelId());
        String liveChatId = youtubeClient.findLiveChatId(channelId);
        if (liveChatId == null) {
            log.warn("No live chat found for YouTube channel {}", channelId);
            return;
        }

        activePollers.put(channelId, new PollState(liveChatId, null, 5000));
        log.info("Started polling YouTube chat for channel {}", channelId);
    }

    @KafkaListener(topics = "raw-sessions-offline")
    public void onSessionOffline(SessionWithUser session) {
        if (!"youtube".equals(session.platform())) return;
        activePollers.remove(String.valueOf(session.channelId()));
    }

    @Scheduled(fixedDelay = 1000)
    public void pollAll() {
        activePollers.forEach((channelId, state) -> {
            if (System.currentTimeMillis() < state.nextPollAt) return;

            try {
                var result = youtubeClient.pollChatMessages(
                    state.liveChatId, state.pageToken);
                // Update next poll time based on API response
                state.nextPollAt = System.currentTimeMillis() + state.pollingIntervalMs;
                state.pageToken = result.nextPageToken();

                for (var msg : result.messages()) {
                    ChatMessage chatMessage = new ChatMessage(
                        "youtube",
                        channelId,
                        msg.getAuthorDetails().getChannelId(),
                        msg.getAuthorDetails().getDisplayName(),
                        msg.getSnippet().getDisplayMessage(),
                        Instant.ofEpochMilli(msg.getSnippet().getPublishedAt().getValue()),
                        null // session ref added downstream
                    );
                    kafkaProducer.send(chatMessage);
                }
            } catch (Exception e) {
                log.error("Error polling YouTube chat for channel {}", channelId, e);
            }
        });
    }

    record PollState(String liveChatId, String pageToken, long pollingIntervalMs) {
        long nextPollAt = 0;
    }
}
```

**Files to create:**
- `chatalytics-youtube-producer/build.gradle`
- `chatalytics-youtube-producer/src/main/java/space/forloop/chatalytics/youtube/producer/YouTubeProducerApplication.java`
- `chatalytics-youtube-producer/src/main/java/space/forloop/chatalytics/youtube/producer/YouTubeChatPoller.java`
- `chatalytics-youtube-producer/src/main/java/space/forloop/chatalytics/youtube/producer/KafkaProducerService.java`
- `chatalytics-youtube-producer/src/main/resources/application.yml`

**application.yml:**
```yaml
spring:
  application:
    name: chatalytics-youtube-producer
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:kafka:9092}

youtube:
  api-key: ${YOUTUBE_API_KEY}
```

**Infra:**
- `chatalytics-infra/services/youtube-producer.yaml`
- `settings.gradle` — add `chatalytics-youtube-library` and `chatalytics-youtube-producer`
- `deploy.sh` — add `youtube-producer` to `JAVA_SERVICES`

### 1C. YouTube Quota Management

| API Call | Cost (units) | Frequency | Daily usage estimate |
|----------|-------------|-----------|---------------------|
| `liveChatMessages.list` | 5 | Every 5-10s per stream | ~43,200/stream/day |
| `search.list` (find live) | 100 | Every 60s per channel | ~1,440/channel/day |
| `videos.list` | 1 | Once per stream start | Negligible |
| `channels.list` | 1 | On user lookup | Negligible |

**Default quota: 10,000 units/day** — supports ~0.2 concurrent streams. Must request quota increase to 1M+ units for production.

**Mitigation strategies:**
- Cache `liveChatId` for the duration of a stream (don't re-lookup)
- Use `search.list` sparingly — only check for new streams every 60s
- Implement `YouTubeQuotaTracker` that tracks daily usage and pauses polling at 90% quota
- Prioritize streams with highest viewer counts

### 1D. Verification

1. Configure Google API credentials and verify quota
2. Add a test YouTube channel to the `chat.user` table with `platform='youtube'`
3. Deploy `youtube-producer` — verify it detects live streams
4. Confirm messages appear in Kafka `raw-messages` with key `youtube:{channelId}`
5. Verify `irc-consumer` (rename to `chat-consumer` in future) writes YouTube messages to PostgreSQL
6. Check LiveMetrics works for YouTube streams
7. Verify frontend displays YouTube sessions with platform badge

---

## Phase 2: Kick Producer

**Goal:** Add Kick chat ingestion via the official Kick API (webhooks + Pusher).

### 2A. New Library: `chatalytics-kick-library/lib`

```java
// KickApiClient.java
@Service
public class KickApiClient {

    private final RestClient restClient;
    private final KickOAuth2TokenManager tokenManager;

    // Register webhooks for a channel
    public void subscribeToChannel(String channelSlug) {
        // POST https://api.kick.com/public/v1/events/subscriptions
        // Events: ChatMessageSent, StreamStarted, StreamEnded
        restClient.post()
            .uri("https://api.kick.com/public/v1/events/subscriptions")
            .header("Authorization", "Bearer " + tokenManager.getAccessToken())
            .body(Map.of(
                "events", List.of(
                    Map.of("name", "chat.message.sent", "version", 1),
                    Map.of("name", "stream.started", "version", 1),
                    Map.of("name", "stream.ended", "version", 1)
                ),
                "method", "webhook",
                "broadcaster_user_id", resolveUserId(channelSlug)
            ))
            .retrieve()
            .toBodilessEntity();
    }

    public KickChannel findChannelBySlug(String slug) {
        return restClient.get()
            .uri("https://api.kick.com/public/v1/channels?slug={slug}", slug)
            .header("Authorization", "Bearer " + tokenManager.getAccessToken())
            .retrieve()
            .body(KickChannel.class);
    }
}
```

```java
// KickOAuth2TokenManager.java — OAuth 2.1 with PKCE
@Service
public class KickOAuth2TokenManager {

    // Kick uses id.kick.com for OAuth
    // Grant type: client_credentials for server-to-server
    // Token endpoint: https://id.kick.com/oauth/token

    private String accessToken;
    private Instant expiresAt;

    public synchronized String getAccessToken() {
        if (accessToken == null || Instant.now().isAfter(expiresAt)) {
            refreshToken();
        }
        return accessToken;
    }
}
```

```java
// KickPlatformService.java
@Service
@RequiredArgsConstructor
public class KickPlatformService implements PlatformService {

    private final KickApiClient client;

    @Override
    public String platform() { return "kick"; }

    @Override
    public Set<PlatformStreamInfo> findOnlineStreams(List<PlatformUser> users) {
        // Kick doesn't have a batch online check
        // Stream status comes via webhooks (StreamStarted/StreamEnded)
        // This method checks current known state from webhook events
        return Set.of(); // Managed by webhook state
    }

    @Override
    public Optional<PlatformUser> findUserByIdentifier(String identifier) {
        KickChannel channel = client.findChannelBySlug(identifier);
        if (channel == null) return Optional.empty();
        return Optional.of(new PlatformUser(
            String.valueOf(channel.userId()),
            channel.slug(),
            channel.user().username(),
            "kick"));
    }
}
```

**Files to create:**
- `chatalytics-kick-library/lib/build.gradle`
- `chatalytics-kick-library/lib/src/main/java/space/forloop/chatalytics/kick/KickApiClient.java`
- `chatalytics-kick-library/lib/src/main/java/space/forloop/chatalytics/kick/KickOAuth2TokenManager.java`
- `chatalytics-kick-library/lib/src/main/java/space/forloop/chatalytics/kick/KickPlatformService.java`
- `chatalytics-kick-library/lib/src/main/java/space/forloop/chatalytics/kick/KickWebhookVerifier.java`
- `chatalytics-kick-library/lib/src/main/java/space/forloop/chatalytics/kick/model/KickChannel.java`
- `chatalytics-kick-library/lib/src/main/java/space/forloop/chatalytics/kick/model/KickChatEvent.java`

### 2B. New Service: `chatalytics-kick-producer`

```java
// KickWebhookController.java
@RestController
@RequestMapping("/webhooks/kick")
@RequiredArgsConstructor
public class KickWebhookController {

    private final KickWebhookVerifier verifier;
    private final KafkaProducerService kafkaProducer;

    @PostMapping
    public ResponseEntity<Void> handleWebhook(
            @RequestHeader("X-Kick-Signature") String signature,
            @RequestBody String rawBody) {

        if (!verifier.verify(signature, rawBody)) {
            return ResponseEntity.status(401).build();
        }

        KickWebhookEvent event = parseEvent(rawBody);

        switch (event.type()) {
            case "chat.message.sent" -> {
                KickChatEvent chat = event.data(KickChatEvent.class);
                ChatMessage message = new ChatMessage(
                    "kick",
                    String.valueOf(chat.broadcasterUserId()),
                    String.valueOf(chat.senderUserId()),
                    chat.senderUsername(),
                    chat.content(),
                    chat.createdAt(),
                    null
                );
                kafkaProducer.send(message);
            }
            case "stream.started" -> {
                // Publish to raw-sessions-online
            }
            case "stream.ended" -> {
                // Publish to raw-sessions-offline
            }
        }

        return ResponseEntity.ok().build();
    }
}
```

**Files to create:**
- `chatalytics-kick-producer/build.gradle`
- `chatalytics-kick-producer/src/main/java/space/forloop/chatalytics/kick/producer/KickProducerApplication.java`
- `chatalytics-kick-producer/src/main/java/space/forloop/chatalytics/kick/producer/KickWebhookController.java`
- `chatalytics-kick-producer/src/main/java/space/forloop/chatalytics/kick/producer/KafkaProducerService.java`
- `chatalytics-kick-producer/src/main/java/space/forloop/chatalytics/kick/producer/KickSubscriptionManager.java`
- `chatalytics-kick-producer/src/main/resources/application.yml`

### 2C. Kick Infra Changes

The webhook endpoint must be publicly reachable. Options:
1. **Ingress rule** — add `/webhooks/kick` path to existing ingress pointing to kick-producer service
2. **Separate LoadBalancer** — dedicated external IP for webhook receiver
3. **Cloudflare Tunnel** — if using Cloudflare, route via tunnel

```yaml
# chatalytics-infra/services/kick-producer.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: kick-producer
  namespace: chatalytics
spec:
  replicas: 1
  template:
    spec:
      containers:
        - name: kick-producer
          image: peavers/chatalytics-kick-producer:latest
          ports:
            - containerPort: 8080
          env:
            - name: KICK_CLIENT_ID
              valueFrom:
                secretKeyRef:
                  name: kick-credentials
                  key: client-id
            - name: KICK_CLIENT_SECRET
              valueFrom:
                secretKeyRef:
                  name: kick-credentials
                  key: client-secret
            - name: KICK_WEBHOOK_SECRET
              valueFrom:
                secretKeyRef:
                  name: kick-credentials
                  key: webhook-secret
---
apiVersion: v1
kind: Service
metadata:
  name: kick-producer
  namespace: chatalytics
spec:
  selector:
    app: kick-producer
  ports:
    - port: 8080
```

### 2D. Kick-Specific Concerns

- **Webhook reliability:** Kick's webhook delivery has known issues. Consider adding a Pusher WebSocket fallback (`pusher-java-client`) that subscribes to `channel.{id}` channels as backup.
- **Rate limits:** Kick API has rate limits of 10 requests/second. Batch subscription creation at startup.
- **App approval:** Kick requires developer app approval before webhooks work. Apply early at `developers.kick.com`.

### 2E. Verification

1. Register Kick developer app and configure OAuth credentials
2. Deploy `kick-producer` with webhook endpoint exposed via ingress
3. Verify webhook subscription registration succeeds for test channels
4. Send test chat on a Kick stream — confirm message flows through pipeline
5. Test webhook signature verification (reject tampered payloads)
6. Verify stream start/stop detection via webhook events
7. Test Pusher fallback if webhooks are unreliable

---

## Phase 3: Rumble Producer

**Goal:** Add Rumble chat ingestion via SSE livestream API.

### 3A. New Library: `chatalytics-rumble-library/lib`

Rumble has no official API — uses an SSE-based livestream API that requires a per-streamer API key.

```java
// RumbleSseClient.java
@Slf4j
@Service
public class RumbleSseClient {

    private final WebClient webClient;

    public Flux<RumbleChatEvent> connectToStream(String apiKey) {
        return webClient.get()
            .uri("https://rum.bll.how/api/chat/{apiKey}/stream", apiKey)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .retrieve()
            .bodyToFlux(String.class)
            .map(this::parseEvent)
            .filter(Objects::nonNull)
            .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(5))
                .maxBackoff(Duration.ofMinutes(5)));
    }

    private RumbleChatEvent parseEvent(String data) {
        // Rumble SSE format:
        // { "type": "messages", "data": { "messages": [...] } }
        // { "type": "init", "data": { "livestreams": [...] } }
        try {
            JsonNode node = objectMapper.readTree(data);
            String type = node.get("type").asText();
            if ("messages".equals(type)) {
                return parseMessages(node.get("data").get("messages"));
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to parse Rumble SSE event", e);
            return null;
        }
    }
}
```

```java
// RumblePlatformService.java
@Service
@RequiredArgsConstructor
public class RumblePlatformService implements PlatformService {

    @Override
    public String platform() { return "rumble"; }

    @Override
    public Set<PlatformStreamInfo> findOnlineStreams(List<PlatformUser> users) {
        // Rumble doesn't have a "check online" API
        // Stream status is detected via SSE `init` event (livestreams array)
        return Set.of(); // Managed by SSE connection state
    }

    @Override
    public Optional<PlatformUser> findUserByIdentifier(String identifier) {
        // Rumble has no public user lookup API
        // Users must be manually added with their API key
        return Optional.empty();
    }
}
```

**Files to create:**
- `chatalytics-rumble-library/lib/build.gradle`
- `chatalytics-rumble-library/lib/src/main/java/space/forloop/chatalytics/rumble/RumbleSseClient.java`
- `chatalytics-rumble-library/lib/src/main/java/space/forloop/chatalytics/rumble/RumblePlatformService.java`
- `chatalytics-rumble-library/lib/src/main/java/space/forloop/chatalytics/rumble/model/RumbleChatEvent.java`
- `chatalytics-rumble-library/lib/src/main/java/space/forloop/chatalytics/rumble/model/RumbleLivestream.java`

### 3B. New Service: `chatalytics-rumble-producer`

```java
// RumbleStreamManager.java
@Slf4j
@Service
@RequiredArgsConstructor
public class RumbleStreamManager {

    private final RumbleSseClient sseClient;
    private final KafkaProducerService kafkaProducer;
    private final RumbleApiKeyRepository apiKeyRepository;

    // channelId -> Disposable (SSE subscription)
    private final ConcurrentHashMap<String, Disposable> activeConnections = new ConcurrentHashMap<>();

    @PostConstruct
    public void connectAll() {
        // Load all Rumble channels with API keys
        apiKeyRepository.findAllRumbleKeys().forEach(entry -> {
            connect(entry.channelId(), entry.apiKey());
        });
    }

    public void connect(String channelId, String apiKey) {
        Disposable subscription = sseClient.connectToStream(apiKey)
            .subscribe(
                event -> handleEvent(channelId, event),
                error -> log.error("SSE error for Rumble channel {}", channelId, error),
                () -> log.info("SSE completed for Rumble channel {}", channelId)
            );
        activeConnections.put(channelId, subscription);
    }

    private void handleEvent(String channelId, RumbleChatEvent event) {
        for (var msg : event.messages()) {
            ChatMessage chatMessage = new ChatMessage(
                "rumble",
                channelId,
                msg.userId(),
                msg.username(),
                msg.text(),
                msg.time(),
                null
            );
            kafkaProducer.send(chatMessage);
        }
    }

    @PreDestroy
    public void disconnectAll() {
        activeConnections.values().forEach(Disposable::dispose);
    }
}
```

**Files to create:**
- `chatalytics-rumble-producer/build.gradle`
- `chatalytics-rumble-producer/src/main/java/space/forloop/chatalytics/rumble/producer/RumbleProducerApplication.java`
- `chatalytics-rumble-producer/src/main/java/space/forloop/chatalytics/rumble/producer/RumbleStreamManager.java`
- `chatalytics-rumble-producer/src/main/java/space/forloop/chatalytics/rumble/producer/KafkaProducerService.java`
- `chatalytics-rumble-producer/src/main/resources/application.yml`

### 3C. API Key Storage

Rumble requires per-streamer API keys. Add storage:

```sql
-- Migration 0023-rumble-api-keys.sql
ALTER TABLE chat.streamer_platform_link ADD COLUMN api_key VARCHAR(512);
ALTER TABLE chat.streamer_platform_link ADD COLUMN api_key_encrypted BOOLEAN DEFAULT false;
```

Or use Kubernetes Secrets / external secret manager for sensitive keys.

### 3D. Rumble-Specific Concerns

- **No public API:** Rumble does not have an official documented API. The SSE endpoint is community-discovered and may change without notice.
- **API key requirement:** Each streamer must provide their Rumble API key (found in Rumble dashboard). No way to monitor arbitrary channels.
- **50-message window:** The SSE `init` event only includes the last 50 messages. Must maintain a persistent connection to avoid gaps.
- **Reconnection:** SSE connections will drop. Use exponential backoff with jitter (5s initial, 5min max).
- **No user lookup:** Cannot look up Rumble users programmatically. Manual onboarding only.
- **Message format:** Rumble messages may contain Rumble-specific formatting (rants, raids) that needs normalization.

### 3E. Verification

1. Obtain a test Rumble API key from a willing streamer
2. Deploy `rumble-producer` — verify SSE connection established
3. Confirm `init` event received with livestream data
4. Send test chat — confirm message flows through to PostgreSQL with `platform=rumble`
5. Test reconnection: kill the SSE connection, verify it reconnects with backoff
6. Test with streamer going offline/online — verify session lifecycle

---

## Phase 4: Cross-Platform Features

**Goal:** Unified multi-platform views, streamer identity linking, and comparative analytics.

### 4A. Streamer Identity Management

Admin UI for linking accounts across platforms using the `chat.streamer` + `chat.streamer_platform_link` tables created in Phase 0.

```java
// StreamerLinkingService.java
@Service
@RequiredArgsConstructor
public class StreamerLinkingService {

    private final DSLContext dsl;

    public Streamer createStreamer(String canonicalName) {
        // INSERT INTO chat.streamer ...
    }

    public void linkPlatform(long streamerId, String platform,
                             long platformUserId, String platformLogin) {
        // INSERT INTO chat.streamer_platform_link ...
    }

    public List<PlatformLink> getLinkedAccounts(long streamerId) {
        // SELECT from chat.streamer_platform_link WHERE streamer_id = ?
    }

    public Optional<Streamer> findByPlatformUser(String platform, long platformUserId) {
        // Reverse lookup: find canonical streamer from any platform account
    }
}
```

**API endpoints:**
- `POST /admin/streamers` — create canonical streamer
- `POST /admin/streamers/{id}/link` — link a platform account
- `GET /admin/streamers/{id}/links` — list linked accounts
- `DELETE /admin/streamers/{id}/links/{linkId}` — unlink

**Frontend:**
- Admin panel with streamer identity management
- Auto-suggest linking when channels have similar names across platforms

### 4B. Combined Dashboard

When a streamer has linked accounts, show a unified view:

```java
// CrossPlatformStatsService.java
@Service
public class CrossPlatformStatsService {

    public CombinedStats getCombinedStats(long streamerId) {
        List<PlatformLink> links = linkingService.getLinkedAccounts(streamerId);

        // Aggregate across all platforms
        long totalMessages = links.stream()
            .mapToLong(l -> messageRepo.countAllMessages(l.platformUserId()))
            .sum();

        long totalChatters = links.stream()
            .mapToLong(l -> messageRepo.countDistinctAuthors(l.platformUserId()))
            .sum();

        return new CombinedStats(totalMessages, totalChatters, ...);
    }
}
```

**Frontend components:**
- `CrossPlatformDashboard.tsx` — side-by-side platform stats
- Platform selector dropdown (All / Twitch / YouTube / Kick / Rumble)
- Platform badges on channel cards and session lists
- Combined live metrics view showing all platforms simultaneously

### 4C. Platform Comparison Views

For simulcasting streamers, compare metrics across platforms:

| Metric | Twitch | YouTube | Kick | Rumble |
|--------|--------|---------|------|--------|
| Messages/min | 142 | 38 | 22 | 8 |
| Active chatters | 2,841 | 612 | 340 | 95 |
| Viewer count | 24,500 | 8,200 | 4,100 | 1,200 |

**Frontend:**
- `PlatformComparisonChart.tsx` — side-by-side bar/line charts
- Real-time comparison during simulcasts
- Historical comparison across sessions

### 4D. Verification

1. Create a test streamer with accounts linked across 2+ platforms
2. Verify combined stats aggregate correctly
3. Test platform comparison view with simultaneous sessions
4. Verify admin UI for linking/unlinking accounts
5. Test platform selector filters data correctly

---

## Kafka Topic Strategy

Single shared topics with platform-prefixed keys:

| Topic | Key Format | Example |
|-------|-----------|---------|
| `raw-messages` | `{platform}:{channelId}` | `twitch:552120296`, `youtube:UCxxxx` |
| `raw-sessions-online` | `{platform}:{channelId}` | `kick:12345` |
| `raw-sessions-offline` | `{platform}:{channelId}` | `rumble:67890` |

This preserves per-channel ordering within a platform while allowing all producers to share the same topics.

---

## Infrastructure Changes Summary

### settings.gradle additions (by phase)
```groovy
// Phase 1
include ':chatalytics-youtube-library'
project(':chatalytics-youtube-library').projectDir = file('chatalytics-youtube-library/lib')
include ':chatalytics-youtube-producer'

// Phase 2
include ':chatalytics-kick-library'
project(':chatalytics-kick-library').projectDir = file('chatalytics-kick-library/lib')
include ':chatalytics-kick-producer'

// Phase 3
include ':chatalytics-rumble-library'
project(':chatalytics-rumble-library').projectDir = file('chatalytics-rumble-library/lib')
include ':chatalytics-rumble-producer'
```

### deploy.sh updates
```bash
# Phase 1: add youtube-producer
# Phase 2: add kick-producer
# Phase 3: add rumble-producer
JAVA_SERVICES=(api irc-consumer irc-producer session-manager youtube-producer kick-producer rumble-producer)
```

### K8s deployments
- `chatalytics-infra/services/youtube-producer.yaml`
- `chatalytics-infra/services/kick-producer.yaml` (+ ingress for webhooks)
- `chatalytics-infra/services/rumble-producer.yaml`

### Secrets
- `youtube-credentials` — Google API key
- `kick-credentials` — client ID, client secret, webhook secret
- Rumble API keys stored in DB per-streamer

---

## Implementation Order Summary

| Phase | Scope | Risk | Dependencies | Estimated Effort |
|-------|-------|------|-------------|-----------------|
| **0** | Schema rename, JOOQ regen, ChatMessage DTO, PlatformService, API/frontend | Medium-High | None | **DONE** |
| **1** | YouTube library + producer | Medium (quota) | Phase 0 | Library + service + quota request |
| **2** | Kick library + producer | Medium-High (webhooks, infra) | Phase 0 | Library + service + ingress setup |
| **3** | Rumble library + producer | High (unofficial API) | Phase 0 | Library + service + key management |
| **4** | Cross-platform features | Low | Phases 1-3 | UI + aggregation services |
