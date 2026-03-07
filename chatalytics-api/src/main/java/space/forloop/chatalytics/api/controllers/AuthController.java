package space.forloop.chatalytics.api.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal OAuth2User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        return ResponseEntity.ok(Map.of(
                "twitchId", user.getAttribute("twitchId"),
                "login", user.getAttribute("login"),
                "displayName", user.getAttribute("display_name"),
                "profileImageUrl", user.getAttribute("profile_image_url")
        ));
    }
}
