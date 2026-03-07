package space.forloop.chatalytics.api.services.wrapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import space.forloop.chatalytics.api.dto.ChannelDtos.StreamerVoteResponse;
import space.forloop.chatalytics.data.domain.StreamerRequestSummary;
import space.forloop.chatalytics.data.generated.tables.pojos.User;
import space.forloop.chatalytics.data.repositories.StreamerRequestRepository;
import space.forloop.chatalytics.data.repositories.UserRepository;
import space.forloop.chatalytics.data.repositories.ViewerRepository;
import space.forloop.chatalytics.twitch.model.TwitchUser;
import space.forloop.chatalytics.twitch.service.TwitchService;

import java.time.ZoneOffset;
import java.util.List;

import static space.forloop.chatalytics.api.util.CacheConstants.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamerRequestService {

    private static final int VOTE_THRESHOLD = 10;
    private static final String ADMIN_LOGIN = "peavers";

    private final StreamerRequestRepository streamerRequestRepository;
    private final UserRepository userRepository;
    private final ViewerRepository viewerRepository;
    private final TwitchService twitchService;

    @Cacheable(value = PENDING_REQUESTS, key = "'page:' + #offset + ':' + #limit")
    public List<StreamerRequestSummary> listPendingPaged(int limit, int offset) {
        return streamerRequestRepository.findPendingPaged(limit, offset);
    }

    @Cacheable(value = PENDING_REQUESTS, key = "'count'")
    public long countPending() {
        return streamerRequestRepository.countPending();
    }

    public List<StreamerRequestSummary> listPendingRequests() {
        return streamerRequestRepository.findAllPending();
    }

    @Caching(evict = {
            @CacheEvict(value = PENDING_REQUESTS, allEntries = true),
            @CacheEvict(value = CHANNEL_DIRECTORY, allEntries = true)
    })
    public StreamerVoteResponse vote(String streamerLogin, long viewerTwitchId) {
        String login = streamerLogin.toLowerCase();

        if (userRepository.findByLogin(login).isPresent()) {
            return new StreamerVoteResponse(false, 0, true);
        }

        boolean isAdmin = viewerRepository.findByTwitchId(viewerTwitchId)
                .map(v -> ADMIN_LOGIN.equalsIgnoreCase(v.login()))
                .orElse(false);

        if (!isAdmin && streamerRequestRepository.existsByStreamerLoginAndRequestedBy(login, viewerTwitchId)) {
            long count = streamerRequestRepository.countByStreamerLogin(login);
            return new StreamerVoteResponse(false, count, false);
        }

        TwitchUser twitchUser;
        try {
            twitchUser = twitchService.findUserByLogin(login);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Streamer not found on Twitch");
        }

        if (isAdmin) {
            boolean added = autoAddStreamer(twitchUser);
            return new StreamerVoteResponse(true, VOTE_THRESHOLD, added);
        }

        streamerRequestRepository.save(
                login,
                twitchUser.id(),
                twitchUser.displayName(),
                twitchUser.profileImageUrl(),
                viewerTwitchId);

        long voteCount = streamerRequestRepository.countByStreamerLogin(login);
        boolean added = false;

        if (voteCount >= VOTE_THRESHOLD) {
            added = autoAddStreamer(twitchUser);
        }

        return new StreamerVoteResponse(true, voteCount, added);
    }

    private boolean autoAddStreamer(TwitchUser twitchUser) {
        try {
            User user = new User(
                    twitchUser.id(),
                    twitchUser.login(),
                    twitchUser.displayName(),
                    twitchUser.type(),
                    twitchUser.broadcasterType(),
                    twitchUser.description(),
                    twitchUser.profileImageUrl(),
                    twitchUser.offlineImageUrl(),
                    twitchUser.viewCount(),
                    twitchUser.createdAt() != null ? twitchUser.createdAt().toInstant(ZoneOffset.UTC) : null
            );
            userRepository.save(user);
            log.info("Auto-added streamer after reaching vote threshold: {}", twitchUser.login());
            return true;
        } catch (Exception e) {
            log.error("Failed to auto-add streamer: {}", twitchUser.login(), e);
            return false;
        }
    }

}
