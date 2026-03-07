package space.forloop.chatalytics.api.dto;

import java.io.Serializable;

public class ChannelDtos {

    public record TwitchUserResult(
            long id,
            String login,
            String displayName,
            String profileImageUrl,
            String broadcasterType,
            boolean alreadyTracked
    ) implements Serializable {}

    public record StreamerVoteRequest(String streamerLogin) {}

    public record StreamerVoteResponse(boolean voted, long voteCount, boolean added) {}

}
