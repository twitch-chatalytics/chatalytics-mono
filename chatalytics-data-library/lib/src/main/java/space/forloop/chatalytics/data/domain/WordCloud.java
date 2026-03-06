package space.forloop.chatalytics.data.domain;

import java.io.Serializable;

public record WordCloud(String word, int frequency) implements Serializable {
}
