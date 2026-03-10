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

    @GetMapping("/channel/{channelId}/authenticity")
    @Cacheable(value = CHANNEL_AUTHENTICITY, key = "#channelId")
    public ChannelAuthenticity channelAuthenticity(@PathVariable long channelId) {
        return channelAuthenticityRepository.findByChannelId(channelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/session/{sessionId}/authenticity")
    @Cacheable(value = SESSION_AUTHENTICITY, key = "#sessionId")
    public SessionAuthenticity sessionAuthenticity(@PathVariable long sessionId) {
        return sessionAuthenticityRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/channel/{channelId}/sessions")
    public List<SessionAuthenticity> channelSessions(
            @PathVariable long channelId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return sessionAuthenticityRepository.findByChannelId(channelId, limit, offset);
    }

    @GetMapping("/channel/{channelId}/trend")
    public List<AuthenticityTrendPoint> channelTrend(
            @PathVariable long channelId,
            @RequestParam(defaultValue = "50") int limit) {
        return sessionAuthenticityRepository.findTrendByChannelId(channelId, limit);
    }

    @GetMapping("/channel/{channelId}/socialblade")
    @Cacheable(value = SOCIALBLADE_CHANNEL, key = "#channelId")
    public SocialBladeChannel socialBladeChannel(@PathVariable long channelId) {
        return socialBladeRepository.findByChannelId(channelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/channel/{channelId}/socialblade/daily")
    @Cacheable(value = SOCIALBLADE_DAILY, key = "#channelId")
    public List<SocialBladeDailyPoint> socialBladeDaily(
            @PathVariable long channelId,
            @RequestParam(defaultValue = "90") int limit) {
        return socialBladeRepository.findDailyByChannelId(channelId, limit);
    }

    @GetMapping("/channel/{channelId}/benchmark")
    @Cacheable(value = CHANNEL_BENCHMARK, key = "#channelId")
    public ChannelBenchmark channelBenchmark(@PathVariable long channelId) {
        return benchmarkService.computeBenchmark(channelId);
    }

    @GetMapping("/channel/{channelId}/brand-safety")
    @Cacheable(value = CHANNEL_BRAND_SAFETY, key = "#channelId")
    public ChannelBrandSafety channelBrandSafety(@PathVariable long channelId) {
        return brandSafetyRepository.findByChannelId(channelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/me")
    public ResponseEntity<?> advertiserMe(@AuthenticationPrincipal OAuth2User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }
        long channelId = (long) user.getAttribute("channelId");
        return advertiserAccountRepository.findByViewerId(channelId)
                .map(account -> ResponseEntity.ok(Map.of(
                        "tier", account.tier(),
                        "status", account.status(),
                        "expiresAt", account.expiresAt() != null ? account.expiresAt().toString() : ""
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    // ─── Alerts ───

    @GetMapping("/channel/{channelId}/alerts/rules")
    public List<AlertRule> alertRules(@PathVariable long channelId) {
        return alertRepository.findRulesByChannelId(channelId);
    }

    @PostMapping("/channel/{channelId}/alerts/rules")
    public AlertRule createAlertRule(@PathVariable long channelId, @RequestBody Map<String, Object> body) {
        String alertType = (String) body.get("alertType");
        Double thresholdValue = body.get("thresholdValue") != null
                ? ((Number) body.get("thresholdValue")).doubleValue()
                : null;

        AlertRule rule = new AlertRule(null, channelId, alertType, thresholdValue, true, null);
        return alertRepository.saveRule(rule);
    }

    @DeleteMapping("/alerts/rules/{ruleId}")
    public ResponseEntity<Void> deleteAlertRule(@PathVariable long ruleId) {
        alertRepository.deleteRule(ruleId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/channel/{channelId}/alerts/events")
    @Cacheable(value = ALERT_EVENTS, key = "#channelId + '-' + #limit")
    public List<AlertEvent> alertEvents(
            @PathVariable long channelId,
            @RequestParam(defaultValue = "50") int limit) {
        return alertRepository.findEventsByChannelId(channelId, limit);
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

    @GetMapping("/channel/{channelId}/campaigns")
    public List<Campaign> listCampaigns(@PathVariable long channelId) {
        return campaignRepository.findByChannelId(channelId);
    }

    @PostMapping("/channel/{channelId}/campaigns")
    public Campaign createCampaign(@PathVariable long channelId, @RequestBody CreateCampaignRequest request) {
        Campaign campaign = new Campaign(
                null,
                channelId,
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
