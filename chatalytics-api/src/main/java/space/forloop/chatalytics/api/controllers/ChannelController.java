package space.forloop.chatalytics.api.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import space.forloop.chatalytics.api.dto.ChannelDtos.StreamerVoteRequest;
import space.forloop.chatalytics.api.dto.ChannelDtos.StreamerVoteResponse;
import space.forloop.chatalytics.api.dto.ChannelDtos.TwitchUserResult;
import space.forloop.chatalytics.api.services.wrapper.ChannelDirectoryService;
import space.forloop.chatalytics.api.services.wrapper.GlobalStatsService;
import space.forloop.chatalytics.api.services.wrapper.StreamerRequestService;
import space.forloop.chatalytics.data.domain.ChannelProfile;
import space.forloop.chatalytics.data.domain.FeaturedChannel;
import space.forloop.chatalytics.data.domain.StreamerRequestSummary;

import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@RequestMapping("/public/channels")
@RestController
public class ChannelController {

    private final ChannelDirectoryService channelDirectoryService;
    private final StreamerRequestService streamerRequestService;
    private final GlobalStatsService globalStatsService;

    @GetMapping
    public List<ChannelProfile> listChannels() {
        return channelDirectoryService.listTrackedChannels();
    }

    @GetMapping("/by-login/{login}")
    public ResponseEntity<ChannelProfile> getByLogin(@PathVariable String login) {
        ChannelProfile profile = channelDirectoryService.findByLogin(login);
        if (profile == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(profile);
    }

    @GetMapping("/search")
    public List<TwitchUserResult> searchChannels(@RequestParam String q) {
        return channelDirectoryService.searchTwitch(q);
    }

    @GetMapping("/requests")
    public List<StreamerRequestSummary> listRequests() {
        return streamerRequestService.listPendingRequests();
    }

    @GetMapping("/requests/paged")
    public Map<String, Object> listRequestsPaged(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return Map.of(
                "items", streamerRequestService.listPendingPaged(limit, offset),
                "total", streamerRequestService.countPending()
        );
    }

    @GetMapping("/featured")
    public List<FeaturedChannel> featuredChannels() {
        return globalStatsService.getFeaturedChannels();
    }

    @GetMapping("/global-stats")
    public ResponseEntity<?> globalStats() {
        return globalStatsService.getStats()
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping("/request")
    public ResponseEntity<?> requestStreamer(
            @RequestBody StreamerVoteRequest request,
            @AuthenticationPrincipal OAuth2User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }
        long channelId = (long) user.getAttribute("channelId");
        return ResponseEntity.ok(streamerRequestService.vote(request.streamerLogin(), channelId));
    }
}
