package space.forloop.chatalytics.data.platform;

public record PlatformStreamInfo(
    String userId,
    String userLogin,
    String gameName,
    String title,
    int viewerCount
) {}
