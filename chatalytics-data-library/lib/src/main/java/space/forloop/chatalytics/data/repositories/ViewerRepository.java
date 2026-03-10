package space.forloop.chatalytics.data.repositories;

import space.forloop.chatalytics.data.domain.Viewer;

import java.util.Optional;

public interface ViewerRepository {

    Optional<Viewer> findByChannelId(long channelId);

    Viewer save(Viewer viewer);

}
