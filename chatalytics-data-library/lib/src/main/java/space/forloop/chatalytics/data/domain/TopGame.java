package space.forloop.chatalytics.data.domain;

import java.io.Serializable;

public record TopGame(String gameName, long sessionCount) implements Serializable {}
