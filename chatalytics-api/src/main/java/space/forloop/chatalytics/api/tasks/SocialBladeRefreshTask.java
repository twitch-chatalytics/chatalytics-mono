package space.forloop.chatalytics.api.tasks;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import space.forloop.chatalytics.api.services.SocialBladeService;
import space.forloop.chatalytics.data.repositories.SocialBladeRepository;
import space.forloop.chatalytics.data.repositories.UserRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class SocialBladeRefreshTask {

    private final SocialBladeRepository socialBladeRepository;
    private final SocialBladeService socialBladeService;
    private final UserRepository userRepository;

    @Scheduled(fixedRate = 6, timeUnit = TimeUnit.HOURS)
    public void refreshSocialBladeData() {
        if (!socialBladeService.isConfigured()) {
            return;
        }

        // Only refresh featured channels (server key has limited quota)
        List<Long> staleIds = socialBladeRepository.findStaleChannelIds(24);
        if (staleIds.isEmpty()) {
            return;
        }

        // Filter to featured channels only (avoids N+1 queries)
        Set<Long> featuredIds = new HashSet<>(userRepository.findFeaturedIds());
        List<Long> featuredStaleIds = staleIds.stream()
                .filter(featuredIds::contains)
                .toList();

        if (featuredStaleIds.isEmpty()) {
            return;
        }

        log.info("Refreshing SocialBlade data for {} featured channels", featuredStaleIds.size());

        for (long channelId : featuredStaleIds) {
            try {
                userRepository.findById(channelId).ifPresent(user -> {
                    String login = user.getLogin();
                    if (login != null && !login.isBlank()) {
                        socialBladeService.fetchAndStore(channelId, login);
                    }
                });
                // Stagger requests to avoid rate limiting (~7s between calls)
                Thread.sleep(7000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.error("Failed to refresh SocialBlade for channelId {}: {}", channelId, e.getMessage());
            }
        }
    }
}
