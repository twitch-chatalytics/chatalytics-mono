package space.forloop.chatalytics.data.domain;

import java.io.Serializable;

public record RepeatedMessage(String text, long count) implements Serializable {}
