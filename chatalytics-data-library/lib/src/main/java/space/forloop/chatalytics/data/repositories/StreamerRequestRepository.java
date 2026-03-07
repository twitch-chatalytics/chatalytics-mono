package space.forloop.chatalytics.data.repositories;

import space.forloop.chatalytics.data.domain.StreamerRequestSummary;

import java.util.List;

public interface StreamerRequestRepository {

    void save(String streamerLogin, Long streamerId, String displayName, String profileImageUrl, long requestedBy);

    long countByStreamerLogin(String streamerLogin);

    List<StreamerRequestSummary> findAllPending();

    boolean existsByStreamerLoginAndRequestedBy(String streamerLogin, long requestedBy);

    List<StreamerRequestSummary> findPendingPaged(int limit, int offset);

    long countPending();

}
