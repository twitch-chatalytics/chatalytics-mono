package space.forloop.chatalytics.data.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

public record SessionAuthenticity(
        long sessionId,
        long channelId,
        int authenticityScore,
        String confidenceLevel,

        // Chat-to-Viewer Ratio signal
        Double chatViewerRatio,
        Double expectedChatRatio,
        Double chatRatioDeviation,

        // Message Quality signal
        Double vocabularyDiversity,
        Double emoteOnlyRatio,
        Double repetitiveMessageRatio,

        // Chatter Behavior signal
        Double singleMessageChatterRatio,
        Double timingUniformityScore,

        // Engagement Authenticity signal
        Double organicFlowScore,
        Double conversationDepthScore,

        // Cross-Session Consistency signal
        Double viewerChatCorrelation,

        List<SuspiciousFlag> suspiciousPatternFlags,
        int algorithmVersion,
        Instant computedAt,
        Instant sessionStartTime
) implements Serializable {

    public record SuspiciousFlag(
            String flag,
            String detail
    ) implements Serializable {}
}
