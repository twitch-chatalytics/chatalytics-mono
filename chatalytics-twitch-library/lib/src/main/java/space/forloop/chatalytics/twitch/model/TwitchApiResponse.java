package space.forloop.chatalytics.twitch.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Data
@RequiredArgsConstructor
@AllArgsConstructor
public class TwitchApiResponse {
    private List<StreamData> data;

    private Pagination pagination;
}
