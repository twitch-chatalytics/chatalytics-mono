package space.forloop.chatalytics.twitch.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import space.forloop.chatalytics.twitch.client.TwitchApiClient;
import space.forloop.chatalytics.twitch.exception.TwitchApiException;
import space.forloop.chatalytics.twitch.model.StreamData;
import space.forloop.chatalytics.twitch.model.TwitchApiResponse;
import space.forloop.chatalytics.twitch.model.TwitchUser;
import space.forloop.chatalytics.twitch.model.TwitchUserResponse;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@RequiredArgsConstructor
public class TwitchServiceImpl implements TwitchService {

    private static final int BATCH_SIZE = 100;

    private final TwitchApiClient apiClient;

    private final ExecutorService executor;

    @Override
    public List<TwitchUser> findUsersByLogin(Collection<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return Collections.emptyList();
        }

        log.debug("Looking up {} Twitch users in batches", usernames.size());

        List<TwitchUser> results = new ArrayList<>();

        // Split usernames into batches
        List<List<String>> batches = new ArrayList<>(new ArrayList<>(usernames).stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.groupingBy(username -> 0 / BATCH_SIZE))
                .values());

        // Process each batch
        for (List<String> batch : batches) {
            MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
            batch.forEach(username -> queryParams.add("login", username));

            try {
                TwitchUserResponse response = apiClient.getForObject("/users", TwitchUserResponse.class, queryParams);
                if (response != null && response.data() != null) {
                    results.addAll(response.data());
                }

                // Optional: Add delay between batches to respect rate limits
                if (batches.size() > 1) {
                    Thread.sleep(100); // 100ms delay between batches
                }

            } catch (Exception e) {
                log.error("Error looking up batch of Twitch users: {}", batch, e);
                throw new TwitchApiException("Failed to lookup batch of users", e);
            }
        }

        // Log warning for any users not found
        Set<String> foundUsers = results.stream()
                .map(TwitchUser::login)
                .collect(Collectors.toSet());
        usernames.stream()
                .filter(username -> !foundUsers.contains(username))
                .forEach(username -> log.warn("No user found for login: {}", username));

        return results;
    }

    // Convenience method for single user lookup
    @Override
    public TwitchUser findUserByLogin(String username) {
        return findUsersByLogin(Collections.singletonList(username))
                .stream()
                .findFirst()
                .orElseThrow(() -> new TwitchApiException("No user found for login: " + username));
    }

    @Override
    public Set<StreamData> batchCheckUserOnlineStatus(List<String> logins) {
        log.debug("Checking online status for {} users", logins.size());
        Set<StreamData> results = new HashSet<>();
        String cursor = null;

        do {
            MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
            logins.forEach(login -> queryParams.add("user_login", login));
            if (cursor != null) {
                queryParams.add("after", cursor);
            }

            try {
                TwitchApiResponse response = apiClient.getForObject("/streams", TwitchApiResponse.class, queryParams);
                results.addAll(response.getData());
                cursor = response.getPagination() != null ? response.getPagination().getCursor() : null;
            } catch (Exception e) {
                log.error("Error fetching stream data: {}", e.getMessage());
                cursor = null;
            }
        } while (cursor != null);

        return results;
    }

    @Override
    public List<String> findAllOnlineUsers(List<TwitchUser> users) {
        if (users.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> allLogins = users.stream()
                .map(TwitchUser::login)
                .collect(Collectors.toList());

        Map<Integer, List<String>> groupedLogins = groupLogins(allLogins, BATCH_SIZE);

        List<Future<Set<StreamData>>> futures = groupedLogins.values().stream()
                .map(loginGroup -> executor.submit(() -> batchCheckUserOnlineStatus(loginGroup)))
                .toList();

        Set<StreamData> allOnlineStreams = futures.stream()
                .map(this::getFutureResultSafe)
                .flatMap(Set::stream)
                .collect(Collectors.toSet());

        return allOnlineStreams.stream()
                .map(StreamData::getUserLogin)
                .toList();
    }

    @Override
    public Set<StreamData> findAllOnlineStreams(List<TwitchUser> users) {
        if (users.isEmpty()) {
            return Collections.emptySet();
        }

        List<String> allLogins = users.stream()
                .map(TwitchUser::login)
                .collect(Collectors.toList());

        Map<Integer, List<String>> groupedLogins = groupLogins(allLogins, BATCH_SIZE);

        List<Future<Set<StreamData>>> futures = groupedLogins.values().stream()
                .map(loginGroup -> executor.submit(() -> batchCheckUserOnlineStatus(loginGroup)))
                .toList();

        return futures.stream()
                .map(this::getFutureResultSafe)
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }

    @Override
    public List<StreamData> findTopOnlineUsers() {
        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        queryParams.add("first", "100");

        return apiClient.getForObject("/streams", TwitchApiResponse.class, queryParams).getData();
    }

    private Map<Integer, List<String>> groupLogins(List<String> logins, int batchSize) {
        return IntStream.range(0, logins.size())
                .boxed()
                .collect(Collectors.groupingBy(
                        index -> index / batchSize,
                        Collectors.mapping(logins::get, Collectors.toList())
                ));
    }

    private Set<StreamData> getFutureResultSafe(Future<Set<StreamData>> future) {
        try {
            return future.get();
        } catch (Exception e) {
            log.error("Error while fetching online streamers", e);
            return Collections.emptySet();
        }
    }
}