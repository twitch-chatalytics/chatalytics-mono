package space.forloop.chatalytics.api.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import space.forloop.chatalytics.api.config.AuthenticationFacade;
import space.forloop.chatalytics.api.domain.SummaryResponse;
import space.forloop.chatalytics.api.domain.TopChatterResponse;
import space.forloop.chatalytics.api.services.DataService;
import space.forloop.chatalytics.data.domain.WordCloud;
import space.forloop.chatalytics.data.generated.tables.pojos.Session;

import java.util.List;

@Slf4j
@CrossOrigin
@RestController
@RequiredArgsConstructor
public class DataController {

    private final DataService dataService;

    private final AuthenticationFacade authenticationFacade;


    @GetMapping("/summary")
    public SummaryResponse summary(@RequestParam(required = false) final List<Long> sessionIds, @RequestParam(required = false) final Long userId) {
        if (userId != null) {
            authenticationFacade.setOverriddenTwitchId(userId);
        }

        return dataService.findSessionSummary(sessionIds);
    }

    @GetMapping("/sessions")
    public List<Session> sessions() {
        return dataService.findSessions();
    }

    @GetMapping("/wordCloud")
    public List<WordCloud> wordCloud(@RequestParam(required = false) final List<Long> sessionIds, @RequestParam(required = false) final Long userId) {
        if (userId != null) {
            authenticationFacade.setOverriddenTwitchId(userId);
        }

        return dataService.findWordCloud(sessionIds);
    }

    @GetMapping("/topChatter")
    public TopChatterResponse topChatter(@RequestParam(required = false) final List<Long> sessionIds, @RequestParam(required = false) final Long userId) {
        if (userId != null) {
            authenticationFacade.setOverriddenTwitchId(userId);
        }

        return dataService.findTopChatter(sessionIds);
    }
}