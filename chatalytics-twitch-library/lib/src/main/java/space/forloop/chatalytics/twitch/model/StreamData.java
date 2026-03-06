package space.forloop.chatalytics.twitch.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Data
@RequiredArgsConstructor
@AllArgsConstructor
public class StreamData {
    private String id;
    @JsonProperty("user_id")
    private String userId;
    @JsonProperty("user_login")
    private String userLogin;
    @JsonProperty("user_name")
    private String userName;
    @JsonProperty("game_id")
    private String gameId;
    @JsonProperty("game_name")
    private String gameName;
    private String type;
    private String title;
    @JsonProperty("viewer_count")
    private int viewerCount;
    @JsonProperty("started_at")
    private String startedAt;
    private String language;
    @JsonProperty("thumbnail_url")
    private String thumbnailUrl;
    @JsonProperty("tag_ids")
    private List<String> tagIds;
    private List<String> tags;
    @JsonProperty("is_mature")
    private boolean isMature;
}
