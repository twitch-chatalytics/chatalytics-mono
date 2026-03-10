package space.forloop.chatalytics.session.tasks;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import space.forloop.chatalytics.data.generated.tables.pojos.User;
import space.forloop.chatalytics.data.repositories.UserRepository;
import space.forloop.chatalytics.twitch.model.StreamData;
import space.forloop.chatalytics.twitch.model.TwitchUser;
import space.forloop.chatalytics.twitch.service.TwitchService;

import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequiredArgsConstructor
public class SeedTopStreamersTask {

    private final TwitchService twitchService;
    private final UserRepository userRepository;

    private static Function<TwitchUser, User> toUser() {
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

    @PostMapping("/seed/top-streamers")
    public Map<String, Object> seedTopStreamers(@RequestParam(defaultValue = "250") int count) {
        log.info("Seeding top {} streamers from Twitch Helix API", count);

        // Get existing users to deduplicate
        Set<Long> existingIds = userRepository.findAll().stream()
                .map(User::getId)
                .collect(Collectors.toSet());

        // Fetch top online streams (up to 100 per call via findTopOnlineUsers)
        List<StreamData> topStreams = twitchService.findTopOnlineUsers();
        log.info("Found {} top online streams", topStreams.size());

        // Extract unique user logins not already tracked
        List<String> newLogins = topStreams.stream()
                .filter(s -> !existingIds.contains(Long.parseLong(s.getUserId())))
                .map(StreamData::getUserLogin)
                .distinct()
                .limit(count)
                .collect(Collectors.toList());

        if (newLogins.isEmpty()) {
            log.info("All top streamers already tracked");
            return Map.of("added", 0, "alreadyTracked", existingIds.size());
        }

        // Resolve full user details from Twitch
        List<TwitchUser> twitchUsers = twitchService.findUsersByLogin(newLogins);

        int added = 0;
        List<String> failed = new ArrayList<>();

        for (TwitchUser tu : twitchUsers) {
            if (existingIds.contains(tu.id())) continue;
            try {
                User user = toUser().apply(tu);
                userRepository.save(user);
                added++;
                log.info("Added streamer: {} ({})", tu.login(), tu.id());
            } catch (Exception e) {
                log.error("Failed to add streamer {}: {}", tu.login(), e.getMessage());
                failed.add(tu.login());
            }
        }

        log.info("Seeding complete: added={}, failed={}, alreadyTracked={}",
                added, failed.size(), existingIds.size());

        return Map.of(
                "added", added,
                "failed", failed,
                "alreadyTracked", existingIds.size(),
                "totalOnline", topStreams.size()
        );
    }
}
