package space.forloop.chatalytics.api.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import space.forloop.chatalytics.data.generated.tables.pojos.User;
import space.forloop.chatalytics.data.repositories.UserRepository;

import java.util.List;

@Slf4j
@CrossOrigin
@RequiredArgsConstructor
@RequestMapping("/admin")
@RestController
public class AdminController {

    private final UserRepository userRepository;

    @GetMapping("/users")
    public List<User> findAllUsers() {
        return userRepository.findAll();
    }

}
