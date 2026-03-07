package space.forloop.chatalytics.api.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import space.forloop.chatalytics.api.services.wrapper.ChatterSummaryService;
import space.forloop.chatalytics.api.services.wrapper.PublicChatterProfileService;
import space.forloop.chatalytics.api.services.wrapper.PublicStatsService;
import space.forloop.chatalytics.api.services.wrapper.SessionListService;
import space.forloop.chatalytics.api.services.wrapper.StreamRecapService;
import space.forloop.chatalytics.api.services.wrapper.ChannelProfileService;
import space.forloop.chatalytics.data.domain.ChannelProfile;
import space.forloop.chatalytics.data.domain.ChannelStats;
import space.forloop.chatalytics.data.domain.ChatterProfile;
import space.forloop.chatalytics.data.domain.SessionSummaryView;
import space.forloop.chatalytics.data.domain.StreamRecap;
import space.forloop.chatalytics.data.generated.tables.pojos.Message;
import space.forloop.chatalytics.data.repositories.MessageRepository;
import space.forloop.chatalytics.data.repositories.SessionRepository;

import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@RequestMapping("/public")
@RestController
public class PublicController {

    private final MessageRepository messageRepository;
    private final SessionRepository sessionRepository;
    private final ChatterSummaryService chatterSummaryService;
    private final PublicChatterProfileService publicChatterProfileService;
    private final PublicStatsService publicStatsService;
    private final SessionListService sessionListService;
    private final StreamRecapService streamRecapService;
    private final ChannelProfileService channelProfileService;

    @GetMapping("/channel")
    public ResponseEntity<ChannelProfile> getChannel(@RequestParam Long twitchId) {
        ChannelProfile profile = channelProfileService.getProfile(twitchId);
        if (profile == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(profile);
    }

    @GetMapping("/stats")
    public ChannelStats getStats(@RequestParam Long twitchId) {
        return publicStatsService.getStats(twitchId);
    }

    @GetMapping("/chatter-profile")
    public ResponseEntity<ChatterProfile> getChatterProfile(
            @RequestParam String author,
            @RequestParam Long twitchId) {
        ChatterProfile profile = publicChatterProfileService.getChatterProfile(author, twitchId);
        if (profile == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(profile);
    }

    @GetMapping("/chatter-summary")
    public ResponseEntity<Map<String, String>> getChatterSummary(
            @RequestParam String author,
            @RequestParam Long twitchId) {
        String summary = chatterSummaryService.summarize(author, twitchId);
        if (summary == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("summary", summary));
    }

    @GetMapping("/authors")
    public List<String> searchAuthors(@RequestParam String q, @RequestParam Long twitchId) {
        return messageRepository.searchAuthors(q, twitchId);
    }

    @GetMapping("/messages/{id}/context")
    public ResponseEntity<List<Message>> findContext(
            @PathVariable Long id,
            @RequestParam Long twitchId,
            @RequestParam(defaultValue = "30") int seconds) {
        return messageRepository.findById(id)
                .map(msg -> ResponseEntity.ok(messageRepository.findContext(twitchId, msg.getTimestamp(), seconds)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/messages")
    public List<Message> findByAuthor(
            @RequestParam String author,
            @RequestParam Long twitchId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) Instant beforeTimestamp,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "50") int limit) {
        return messageRepository.findByAuthor(author, twitchId, from, to, beforeTimestamp, beforeId, Math.min(limit, 200));
    }

    @GetMapping("/sessions")
    public List<SessionSummaryView> getSessions(
            @RequestParam Long twitchId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) Instant beforeStartTime,
            @RequestParam(required = false) Long beforeId) {
        int safeLimit = Math.min(limit, 100);
        // First page with no filters → serve from Redis cache
        if (from == null && to == null && beforeStartTime == null && beforeId == null) {
            return sessionListService.findFirstPage(twitchId, safeLimit);
        }
        return sessionRepository.findSessionsWithStats(
                twitchId, safeLimit, from, to, beforeStartTime, beforeId);
    }

    @GetMapping("/sessions/{id}/recap")
    public ResponseEntity<StreamRecap> getSessionRecap(@PathVariable Long id) {
        StreamRecap recap = streamRecapService.generateRecap(id);
        if (recap == null) {
            return ResponseEntity.notFound().build();
        }
        var freshClips = streamRecapService.fetchFreshClips(id, 8);
        return ResponseEntity.ok(recap.withTopClips(freshClips));
    }

}
