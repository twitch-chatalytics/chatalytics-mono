package space.forloop.chatalytics.data.platform;

public record PlatformUser(
    String id,
    String login,
    String displayName,
    String platform
) {}
