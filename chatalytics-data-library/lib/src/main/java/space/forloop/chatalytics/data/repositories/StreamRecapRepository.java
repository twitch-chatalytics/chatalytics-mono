package space.forloop.chatalytics.data.repositories;

import space.forloop.chatalytics.data.domain.StreamRecap;

import java.util.List;
import java.util.Optional;

public interface StreamRecapRepository {

    Optional<StreamRecap> findBySessionId(long sessionId);

    void save(StreamRecap recap);

    List<Long> findSessionIdsWithoutRecap();
}
