package space.forloop.chatalytics.twitch.exception;

import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.DefaultResponseErrorHandler;

import java.io.IOException;
import java.nio.charset.Charset;

public class TwitchResponseErrorHandler extends DefaultResponseErrorHandler {

    @Override
    public void handleError(ClientHttpResponse response) throws IOException {
        String error = StreamUtils.copyToString(response.getBody(), Charset.defaultCharset());
        throw new TwitchApiException("Twitch API error: " + error);
    }
}