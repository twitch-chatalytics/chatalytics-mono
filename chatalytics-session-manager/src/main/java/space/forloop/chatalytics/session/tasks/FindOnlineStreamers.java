package space.forloop.chatalytics.session.tasks;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import space.forloop.chatalytics.data.generated.tables.pojos.User;
import space.forloop.chatalytics.data.repositories.UserRepository;
import space.forloop.chatalytics.session.services.SessionService;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class FindOnlineStreamers {

    private final SessionService sessionService;

    private final UserRepository userRepository;

    @Scheduled(fixedRate = 2, timeUnit = TimeUnit.MINUTES)
    public void sessions() {
        List<User> users = userRepository.findAll();

        sessionService.updateOnlineSessions(users);
    }
}
