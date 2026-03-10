package space.forloop.chatalytics.data.repositories;

import space.forloop.chatalytics.data.generated.tables.pojos.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository {

    Optional<User> findById(long id);

    Optional<User> findByLogin(String login);

    List<User> findAll();

    List<User> findAllOnline();

    User save(User user);

    void saveAll(List<User> users);

    List<Long> findFeaturedIds();

}
