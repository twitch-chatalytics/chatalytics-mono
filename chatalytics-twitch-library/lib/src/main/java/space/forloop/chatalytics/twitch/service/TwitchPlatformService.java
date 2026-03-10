package space.forloop.chatalytics.twitch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import space.forloop.chatalytics.data.platform.PlatformClip;
import space.forloop.chatalytics.data.platform.PlatformService;
import space.forloop.chatalytics.data.platform.PlatformStreamInfo;
import space.forloop.chatalytics.data.platform.PlatformUser;
import space.forloop.chatalytics.twitch.model.TwitchUser;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TwitchPlatformService implements PlatformService {

    private final TwitchService twitchService;

    @Override
    public String platform() {
        return "twitch";
    }

    @Override
    public Set<PlatformStreamInfo> findOnlineStreams(List<PlatformUser> users) {
        List<TwitchUser> twitchUsers = users.stream()
                .map(u -> TwitchUser.builder()
                        .id(Long.parseLong(u.id()))
                        .login(u.login())
                        .displayName(u.displayName())
                        .build())
                .toList();

        return twitchService.findAllOnlineStreams(twitchUsers).stream()
                .map(s -> new PlatformStreamInfo(
                        s.getUserId(),
                        s.getUserLogin(),
                        s.getGameName(),
                        s.getTitle(),
                        s.getViewerCount()))
                .collect(Collectors.toSet());
    }

    @Override
    public Optional<PlatformUser> findUserByIdentifier(String identifier) {
        TwitchUser user = twitchService.findUserByLogin(identifier);
        if (user == null) return Optional.empty();
        return Optional.of(new PlatformUser(
                String.valueOf(user.id()),
                user.login(),
                user.displayName(),
                "twitch"));
    }

    @Override
    public List<PlatformClip> findClips(String channelId, Instant from, Instant to, int limit) {
        return twitchService.findClips(channelId, from, to, limit).stream()
                .map(c -> new PlatformClip(
                        c.id(),
                        c.url(),
                        c.embedUrl(),
                        c.title(),
                        c.viewCount(),
                        Instant.parse(c.createdAt()),
                        c.thumbnailUrl(),
                        c.duration(),
                        c.creatorName()))
                .toList();
    }
}
