package space.forloop.chatalytics.api.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import space.forloop.chatalytics.data.domain.*;
import space.forloop.chatalytics.data.repositories.AdvertiserAccountRepository;
import space.forloop.chatalytics.data.repositories.ChannelAuthenticityRepository;
import space.forloop.chatalytics.data.repositories.SessionAuthenticityRepository;
import space.forloop.chatalytics.data.repositories.SocialBladeRepository;

import java.util.List;
import java.util.Map;

import static space.forloop.chatalytics.api.util.CacheConstants.*;

@RestController
@RequestMapping("/advertiser")
@RequiredArgsConstructor
public class AdvertiserController {

    private final SessionAuthenticityRepository sessionAuthenticityRepository;
    private final ChannelAuthenticityRepository channelAuthenticityRepository;
    private final AdvertiserAccountRepository advertiserAccountRepository;
    private final SocialBladeRepository socialBladeRepository;

    @GetMapping("/channel/{twitchId}/authenticity")
    @Cacheable(value = CHANNEL_AUTHENTICITY, key = "#twitchId")
    public ChannelAuthenticity channelAuthenticity(@PathVariable long twitchId) {
        return channelAuthenticityRepository.findByTwitchId(twitchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/session/{sessionId}/authenticity")
    @Cacheable(value = SESSION_AUTHENTICITY, key = "#sessionId")
    public SessionAuthenticity sessionAuthenticity(@PathVariable long sessionId) {
        return sessionAuthenticityRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/channel/{twitchId}/sessions")
    public List<SessionAuthenticity> channelSessions(
            @PathVariable long twitchId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return sessionAuthenticityRepository.findByTwitchId(twitchId, limit, offset);
    }

    @GetMapping("/channel/{twitchId}/trend")
    public List<AuthenticityTrendPoint> channelTrend(
            @PathVariable long twitchId,
            @RequestParam(defaultValue = "50") int limit) {
        return sessionAuthenticityRepository.findTrendByTwitchId(twitchId, limit);
    }

    @GetMapping("/channel/{twitchId}/socialblade")
    @Cacheable(value = SOCIALBLADE_CHANNEL, key = "#twitchId")
    public SocialBladeChannel socialBladeChannel(@PathVariable long twitchId) {
        return socialBladeRepository.findByTwitchId(twitchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/channel/{twitchId}/socialblade/daily")
    @Cacheable(value = SOCIALBLADE_DAILY, key = "#twitchId")
    public List<SocialBladeDailyPoint> socialBladeDaily(
            @PathVariable long twitchId,
            @RequestParam(defaultValue = "90") int limit) {
        return socialBladeRepository.findDailyByTwitchId(twitchId, limit);
    }

    @GetMapping("/me")
    public ResponseEntity<?> advertiserMe(@AuthenticationPrincipal OAuth2User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }
        long twitchId = (long) user.getAttribute("twitchId");
        return advertiserAccountRepository.findByViewerId(twitchId)
                .map(account -> ResponseEntity.ok(Map.of(
                        "tier", account.tier(),
                        "status", account.status(),
                        "expiresAt", account.expiresAt() != null ? account.expiresAt().toString() : ""
                )))
                .orElse(ResponseEntity.notFound().build());
    }
}
