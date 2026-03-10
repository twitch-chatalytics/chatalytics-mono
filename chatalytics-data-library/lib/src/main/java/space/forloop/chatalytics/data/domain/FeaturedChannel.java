package space.forloop.chatalytics.data.domain;

import java.io.Serializable;

public record FeaturedChannel(
        ChannelProfile channel,
        ChannelStats stats
) implements Serializable {}
