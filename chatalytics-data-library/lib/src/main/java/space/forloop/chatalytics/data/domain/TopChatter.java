package space.forloop.chatalytics.data.domain;

import java.io.Serializable;

public record TopChatter(String author, int messageCount) implements Serializable {
}
