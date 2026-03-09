package space.forloop.chatalytics.api.tasks;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import space.forloop.chatalytics.api.services.SocialBladeService;
import space.forloop.chatalytics.data.repositories.SocialBladeRepository;
import space.forloop.chatalytics.data.repositories.UserRepository;

import java.util.List;
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

        List<Long> staleIds = socialBladeRepository.findStaleChannelIds(24);
        if (staleIds.isEmpty()) {
            return;
        }

        log.info("Refreshing SocialBlade data for {} channels", staleIds.size());

        for (long twitchId : staleIds) {
            try {
                userRepository.findById(twitchId).ifPresent(user -> {
                    String login = user.getLogin();
                    if (login != null && !login.isBlank()) {
                        socialBladeService.fetchAndStore(twitchId, login);
                    }
                });
            } catch (Exception e) {
                log.error("Failed to refresh SocialBlade for twitchId {}: {}", twitchId, e.getMessage());
            }
        }
    }
}
