package space.forloop.chatalytics.api.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import space.forloop.chatalytics.api.services.BenchmarkService;
import space.forloop.chatalytics.api.services.CampaignVerificationService;
import space.forloop.chatalytics.data.domain.*;
import space.forloop.chatalytics.data.repositories.*;

import java.time.LocalDate;
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
    private final BenchmarkService benchmarkService;
    private final BrandSafetyRepository brandSafetyRepository;
    private final AlertRepository alertRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignVerificationService campaignVerificationService;

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

    @GetMapping("/channel/{twitchId}/benchmark")
    @Cacheable(value = CHANNEL_BENCHMARK, key = "#twitchId")
    public ChannelBenchmark channelBenchmark(@PathVariable long twitchId) {
        return benchmarkService.computeBenchmark(twitchId);
    }

    @GetMapping("/channel/{twitchId}/brand-safety")
    @Cacheable(value = CHANNEL_BRAND_SAFETY, key = "#twitchId")
    public ChannelBrandSafety channelBrandSafety(@PathVariable long twitchId) {
        return brandSafetyRepository.findByTwitchId(twitchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
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

    // ─── Alerts ───

    @GetMapping("/channel/{twitchId}/alerts/rules")
    public List<AlertRule> alertRules(@PathVariable long twitchId) {
        return alertRepository.findRulesByTwitchId(twitchId);
    }

    @PostMapping("/channel/{twitchId}/alerts/rules")
    public AlertRule createAlertRule(@PathVariable long twitchId, @RequestBody Map<String, Object> body) {
        String alertType = (String) body.get("alertType");
        Double thresholdValue = body.get("thresholdValue") != null
                ? ((Number) body.get("thresholdValue")).doubleValue()
                : null;

        AlertRule rule = new AlertRule(null, twitchId, alertType, thresholdValue, true, null);
        return alertRepository.saveRule(rule);
    }

    @DeleteMapping("/alerts/rules/{ruleId}")
    public ResponseEntity<Void> deleteAlertRule(@PathVariable long ruleId) {
        alertRepository.deleteRule(ruleId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/channel/{twitchId}/alerts/events")
    @Cacheable(value = ALERT_EVENTS, key = "#twitchId + '-' + #limit")
    public List<AlertEvent> alertEvents(
            @PathVariable long twitchId,
            @RequestParam(defaultValue = "50") int limit) {
        return alertRepository.findEventsByTwitchId(twitchId, limit);
    }

    @GetMapping("/alerts/events/recent")
    public List<AlertEvent> recentAlertEvents() {
        return alertRepository.findRecentEvents(20);
    }

    @PostMapping("/alerts/events/{eventId}/acknowledge")
    public ResponseEntity<Void> acknowledgeEvent(@PathVariable long eventId) {
        alertRepository.acknowledgeEvent(eventId);
        return ResponseEntity.ok().build();
    }

    // ─── Campaigns ───

    @GetMapping("/channel/{twitchId}/campaigns")
    public List<Campaign> listCampaigns(@PathVariable long twitchId) {
        return campaignRepository.findByTwitchId(twitchId);
    }

    @PostMapping("/channel/{twitchId}/campaigns")
    public Campaign createCampaign(@PathVariable long twitchId, @RequestBody CreateCampaignRequest request) {
        Campaign campaign = new Campaign(
                null,
                twitchId,
                request.campaignName(),
                request.brandName(),
                request.brandKeywords() != null ? request.brandKeywords() : List.of(),
                request.startDate(),
                request.endDate(),
                request.dealPrice(),
                null
        );
        return campaignRepository.save(campaign);
    }

    @DeleteMapping("/campaigns/{campaignId}")
    public ResponseEntity<Void> deleteCampaign(@PathVariable long campaignId) {
        campaignRepository.delete(campaignId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/campaigns/{campaignId}/report")
    @Cacheable(value = CAMPAIGN_REPORT, key = "#campaignId")
    public CampaignReport campaignReport(@PathVariable long campaignId) {
        return campaignVerificationService.generateReport(campaignId);
    }

    record CreateCampaignRequest(
            String campaignName,
            String brandName,
            List<String> brandKeywords,
            LocalDate startDate,
            LocalDate endDate,
            Double dealPrice
    ) {}
}
