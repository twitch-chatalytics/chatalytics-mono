package space.forloop.chatalytics.api.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import space.forloop.chatalytics.data.domain.SocialBladeChannel;
import space.forloop.chatalytics.data.domain.SocialBladeDailyPoint;
import space.forloop.chatalytics.data.repositories.SocialBladeRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class SocialBladeService {

    private static final String BASE_URL = "https://matrix.sbapis.com/b";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final SocialBladeRepository repository;
    private final RestTemplate restTemplate;
    private final ObjectMapper mapper;
    private final String clientId;
    private final String token;

    public SocialBladeService(
            SocialBladeRepository repository,
            @Value("${socialblade.clientid:}") String clientId,
            @Value("${socialblade.token:}") String token) {
        this.repository = repository;
        this.restTemplate = new RestTemplate();
        this.mapper = new ObjectMapper();
        this.clientId = clientId;
        this.token = token;
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank() && token != null && !token.isBlank();
    }

    public Optional<SocialBladeChannel> fetchAndStore(long twitchId, String username) {
        return fetchAndStore(twitchId, username, null, null);
    }

    public Optional<SocialBladeChannel> fetchAndStore(long twitchId, String username,
                                                       String overrideClientId, String overrideToken) {
        boolean useOverride = overrideClientId != null && !overrideClientId.isBlank();
        if (!useOverride && !isConfigured()) {
            log.warn("SocialBlade API credentials not configured, skipping fetch for {}", username);
            return repository.findByTwitchId(twitchId);
        }

        String cid = useOverride ? overrideClientId : this.clientId;
        String tok = useOverride ? overrideToken : this.token;

        try {
            JsonNode stats = callApi("/twitch/statistics?query=" + username, cid, tok);
            if (stats == null || stats.has("error")) {
                log.warn("SocialBlade returned error for {}: {}", username,
                        stats != null ? stats.get("error").asText() : "null response");
                return repository.findByTwitchId(twitchId);
            }

            SocialBladeChannel channel = parseTwitchStats(twitchId, stats);

            // Try to get social links from YouTube lookup (SB often cross-references)
            channel = enrichWithSocialLinks(channel, username, cid, tok);

            repository.save(channel);

            // Fetch and store daily history
            List<SocialBladeDailyPoint> dailyPoints = parseDailyHistory(twitchId, stats);
            if (!dailyPoints.isEmpty()) {
                repository.saveDailyPoints(twitchId, dailyPoints);
            }

            log.info("Stored SocialBlade data for {} (twitchId={}, grade={}, followers={})",
                    username, twitchId, channel.grade(), channel.followers());

            return Optional.of(channel);
        } catch (Exception e) {
            log.error("Failed to fetch SocialBlade data for {} (twitchId={}): {}", username, twitchId, e.getMessage());
            return repository.findByTwitchId(twitchId);
        }
    }

    private SocialBladeChannel parseTwitchStats(long twitchId, JsonNode stats) {
        JsonNode data = stats.has("data") ? stats.get("data") : stats;
        JsonNode id = data.has("id") ? data.get("id") : data;
        JsonNode total = data.path("statistics").path("total");
        JsonNode growth = data.path("statistics").path("growth").path("followers");
        JsonNode misc = data.path("misc");
        JsonNode ranks = data.path("ranks");

        return new SocialBladeChannel(
                twitchId,
                textOrNull(id, "username"),
                textOrNull(id, "display_name"),
                longOrNull(total, "followers"),
                longOrNull(total, "views"),
                misc.has("grade") ? textOrNull(misc.get("grade"), "grade") : null,
                intOrNull(ranks, "sbrank"),
                intOrNull(ranks, "followers"),
                intOrNull(growth, "30"),
                intOrNull(growth, "90"),
                intOrNull(growth, "180"),
                null, null, null,  // view gains not provided in growth
                null, null, null, null, null,  // social links filled separately
                Instant.now(),
                Instant.now()
        );
    }

    private SocialBladeChannel enrichWithSocialLinks(SocialBladeChannel channel, String username,
                                                      String cid, String tok) {
        try {
            JsonNode yt = callApi("/youtube/statistics?query=" + username + "&platform=twitch", cid, tok);
            if (yt == null || yt.has("error")) return channel;

            JsonNode data = yt.has("data") ? yt.get("data") : yt;
            JsonNode links = data.has("links") ? data.get("links") : null;
            if (links == null) return channel;

            return new SocialBladeChannel(
                    channel.twitchId(),
                    channel.username(),
                    channel.displayName(),
                    channel.followers(),
                    channel.views(),
                    channel.grade(),
                    channel.rank(),
                    channel.followerRank(),
                    channel.followersGained30d(),
                    channel.followersGained90d(),
                    channel.followersGained180d(),
                    channel.viewsGained30d(),
                    channel.viewsGained90d(),
                    channel.viewsGained180d(),
                    textOrNull(links, "youtube_url"),
                    textOrNull(links, "twitter_url"),
                    textOrNull(links, "instagram_url"),
                    textOrNull(links, "discord_url"),
                    textOrNull(links, "tiktok_url"),
                    channel.fetchedAt(),
                    channel.updatedAt()
            );
        } catch (Exception e) {
            log.debug("Could not enrich social links for {}: {}", username, e.getMessage());
            return channel;
        }
    }

    private List<SocialBladeDailyPoint> parseDailyHistory(long twitchId, JsonNode stats) {
        List<SocialBladeDailyPoint> points = new ArrayList<>();
        JsonNode daily = stats.has("data") && stats.get("data").has("daily")
                ? stats.get("data").get("daily")
                : stats.has("daily") ? stats.get("daily") : null;

        if (daily == null || !daily.isArray()) return points;

        for (JsonNode day : daily) {
            try {
                LocalDate date = LocalDate.parse(day.get("date").asText().substring(0, 10), DATE_FMT);
                points.add(new SocialBladeDailyPoint(
                        twitchId,
                        date,
                        longOrNull(day, "followers"),
                        longOrNull(day, "views"),
                        intOrNull(day, "follower_change"),
                        longOrNull(day, "view_change")
                ));
            } catch (Exception e) {
                log.debug("Skipping malformed daily entry: {}", e.getMessage());
            }
        }

        return points;
    }

    private JsonNode callApi(String path, String cid, String tok) {
        int maxRetries = 3;
        long backoffMs = 2000;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set("clientid", cid);
                headers.set("token", tok);

                ResponseEntity<String> response = restTemplate.exchange(
                        BASE_URL + path,
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        String.class
                );

                if (response.getBody() == null) return null;
                return mapper.readTree(response.getBody());
            } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests e) {
                if (attempt < maxRetries) {
                    long waitMs = backoffMs * (1L << attempt);
                    log.warn("SocialBlade 429 rate limit on {}, retrying in {}ms (attempt {}/{})",
                            path, waitMs, attempt + 1, maxRetries);
                    try { Thread.sleep(waitMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return null; }
                } else {
                    log.error("SocialBlade 429 rate limit exhausted for {}", path);
                    return null;
                }
            } catch (Exception e) {
                log.error("SocialBlade API call failed for {}: {}", path, e.getMessage());
                return null;
            }
        }
        return null;
    }

    private static String textOrNull(JsonNode node, String field) {
        return node != null && node.has(field) && !node.get(field).isNull()
                ? node.get(field).asText() : null;
    }

    private static Long longOrNull(JsonNode node, String field) {
        return node != null && node.has(field) && !node.get(field).isNull()
                ? node.get(field).asLong() : null;
    }

    private static Integer intOrNull(JsonNode node, String field) {
        return node != null && node.has(field) && !node.get(field).isNull()
                ? node.get(field).asInt() : null;
    }
}
