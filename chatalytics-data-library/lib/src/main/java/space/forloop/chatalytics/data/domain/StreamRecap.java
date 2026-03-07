package space.forloop.chatalytics.data.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

public record StreamRecap(
        long sessionId,
        Instant startTime,
        Instant endTime,
        long totalMessages,
        long totalChatters,
        List<StreamSnapshot> snapshots,
        List<ChatActivityBucket> chatActivity,
        List<TopChatter> topChatters,
        String aiSummary,
        double messagesPerMinute,
        double chattersPerMinute,
        Integer peakViewerCount,
        Double avgViewerCount,
        Integer minViewerCount,
        MessageAnalysis messageAnalysis,
        long newChatterCount,
        long returningChatterCount,
        List<TopWord> topWords,
        List<GameSegment> gameSegments,
        Double chatParticipationRate,
        ChatMoment peakMoment,
        List<StreamClip> topClips,
        List<HypeMoment> hypeMoments
) implements Serializable {

    public StreamRecap withTopClips(List<StreamClip> clips) {
        return new StreamRecap(sessionId, startTime, endTime, totalMessages, totalChatters,
                snapshots, chatActivity, topChatters, aiSummary, messagesPerMinute,
                chattersPerMinute, peakViewerCount, avgViewerCount, minViewerCount,
                messageAnalysis, newChatterCount, returningChatterCount, topWords,
                gameSegments, chatParticipationRate, peakMoment, clips, hypeMoments);
    }
}
