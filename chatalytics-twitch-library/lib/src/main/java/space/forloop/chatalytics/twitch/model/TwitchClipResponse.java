package space.forloop.chatalytics.twitch.model;

import java.util.List;

public record TwitchClipResponse(List<TwitchClipData> data, Pagination pagination) {}
