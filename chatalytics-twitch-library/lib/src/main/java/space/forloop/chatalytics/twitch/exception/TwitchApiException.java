package space.forloop.chatalytics.twitch.exception;


public class TwitchApiException extends RuntimeException {
    public TwitchApiException(String message) {
        super(message);
    }

    public TwitchApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
