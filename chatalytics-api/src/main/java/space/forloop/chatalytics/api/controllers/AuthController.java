package space.forloop.chatalytics.api.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal OAuth2User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        List<String> roles = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .toList();

        Map<String, Object> response = new HashMap<>();
        response.put("twitchId", user.getAttribute("twitchId"));
        response.put("login", user.getAttribute("login"));
        response.put("displayName", user.getAttribute("display_name"));
        response.put("profileImageUrl", user.getAttribute("profile_image_url"));
        response.put("roles", roles);

        return ResponseEntity.ok(response);
    }
}
