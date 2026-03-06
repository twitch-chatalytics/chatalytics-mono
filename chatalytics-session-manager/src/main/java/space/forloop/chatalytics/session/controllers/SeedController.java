package space.forloop.chatalytics.session.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import space.forloop.chatalytics.data.generated.tables.pojos.User;
import space.forloop.chatalytics.data.repositories.UserRepository;
import space.forloop.chatalytics.twitch.model.TwitchUser;
import space.forloop.chatalytics.twitch.service.TwitchService;

import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Function;

@Slf4j
@RestController
@RequiredArgsConstructor
public class SeedController {

    private final TwitchService twitchService;

    private final UserRepository userRepository;

    private static Function<TwitchUser, User> getTwitchUserUserFunction() {

        return t -> {
            User user = new User();
            user.setId(t.id());
            user.setLogin(t.login());
            user.setDisplayName(t.displayName());
            user.setType(t.type());
            user.setBroadcasterType(t.broadcasterType());
            user.setDescription(t.description());
            user.setProfileImageUrl(t.profileImageUrl());
            user.setOfflineImageUrl(t.offlineImageUrl());
            user.setViewCount(t.viewCount());
            user.setCreatedAt(t.createdAt().toInstant(ZoneOffset.UTC));
            return user;
        };
    }

    @PostMapping("/users")
    public ResponseEntity<User> addUser(@RequestParam String login) {
        List<TwitchUser> twitchUsers = twitchService.findUsersByLogin(List.of(login));

        if (twitchUsers.isEmpty()) {
            log.warn("User not found on Twitch: {}", login);
            return ResponseEntity.notFound().build();
        }

        User user = getTwitchUserUserFunction().apply(twitchUsers.getFirst());
        userRepository.save(user);

        log.info("Added user: {}", login);
        return ResponseEntity.ok(user);
    }
}
