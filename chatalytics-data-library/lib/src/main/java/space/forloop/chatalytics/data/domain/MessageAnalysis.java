package space.forloop.chatalytics.data.domain;

import java.io.Serializable;

public record MessageAnalysis(
        double avgMessageLength,
        double medianMessageLength,
        long commandCount,
        double shortMessageRatio,
        double capsRatio,
        double questionRatio,
        double exclamationRatio,
        long linkCount
) implements Serializable {}
