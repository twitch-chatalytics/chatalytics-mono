package space.forloop.chatalytics.data.repositories;

import space.forloop.chatalytics.data.generated.tables.pojos.RollupHistory;
import space.forloop.chatalytics.data.generated.tables.pojos.Session;

import java.util.Optional;

public interface RollupRepository {

    Optional<RollupHistory> findLatest(Session session);

    void writePartial(Session session);

    void writeFinal(Session session);

}
