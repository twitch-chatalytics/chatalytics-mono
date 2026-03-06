package space.forloop.chatalytics.data.domain;

import java.io.Serializable;

public record TopWord(String word, long count) implements Serializable {}
