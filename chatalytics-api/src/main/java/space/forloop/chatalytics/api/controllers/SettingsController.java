package space.forloop.chatalytics.api.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.*;
import space.forloop.chatalytics.api.services.ViewerApiKeyService;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final ViewerApiKeyService viewerApiKeyService;

    @PostMapping("/keys")
    public ResponseEntity<Map<String, String>> saveApiKey(
            @RequestBody Map<String, String> body,
            OAuth2AuthenticationToken auth) {

        long viewerChannelId = Long.parseLong(auth.getPrincipal().getName());
        String provider = body.get("provider");
        String apiKey = body.get("apiKey");
        String apiSecret = body.get("apiSecret");

        if (provider == null || apiKey == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "provider and apiKey are required"));
        }

        viewerApiKeyService.storeKey(viewerChannelId, provider, apiKey, apiSecret);
        return ResponseEntity.ok(Map.of("status", "saved", "provider", provider));
    }

    @DeleteMapping("/keys/{provider}")
    public ResponseEntity<Map<String, String>> deleteApiKey(
            @PathVariable String provider,
            OAuth2AuthenticationToken auth) {

        long viewerChannelId = Long.parseLong(auth.getPrincipal().getName());
        viewerApiKeyService.deleteKey(viewerChannelId, provider);
        return ResponseEntity.ok(Map.of("status", "deleted", "provider", provider));
    }

    @GetMapping("/keys/{provider}/status")
    public ResponseEntity<Map<String, Object>> keyStatus(
            @PathVariable String provider,
            OAuth2AuthenticationToken auth) {

        long viewerChannelId = Long.parseLong(auth.getPrincipal().getName());
        boolean hasKey = viewerApiKeyService.hasKey(viewerChannelId, provider);
        return ResponseEntity.ok(Map.of("provider", provider, "configured", hasKey));
    }
}
