package space.forloop.chatalytics.api.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.jooq.Result;
import org.springframework.stereotype.Service;
import space.forloop.chatalytics.data.domain.*;
import space.forloop.chatalytics.data.domain.SessionAuthenticity.SuspiciousFlag;
import space.forloop.chatalytics.data.repositories.StreamRecapRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.jooq.impl.DSL.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticityScoreService {

    private static final int ALGORITHM_VERSION = 1;

    // Signal weights (must sum to 1.0)
    private static final double W_CHAT_RATIO = 0.25;
    private static final double W_MESSAGE_QUALITY = 0.20;
    private static final double W_CHATTER_BEHAVIOR = 0.25;
    private static final double W_ENGAGEMENT = 0.15;
    private static final double W_CROSS_SESSION = 0.15;

    private final StreamRecapRepository streamRecapRepository;
    private final DSLContext dsl;

    public SessionAuthenticity computeScore(long sessionId) {
        Optional<StreamRecap> recapOpt = streamRecapRepository.findBySessionId(sessionId);
        if (recapOpt.isEmpty()) {
            return null;
        }

        StreamRecap recap = recapOpt.get();
        long twitchId = resolveTwitchId(sessionId);
        if (twitchId == 0) return null;

        List<SuspiciousFlag> flags = new ArrayList<>();

        // --- Signal 1: Chat-to-Viewer Ratio (25%) ---
        double chatViewerRatio = recap.chatParticipationRate() != null ? recap.chatParticipationRate() : 0;
        double expectedChatRatio = computeExpectedChatRatio(recap.peakViewerCount());
        double chatRatioDeviation = expectedChatRatio > 0 ? chatViewerRatio / expectedChatRatio : 1.0;
        double chatRatioSignal = clampSignal(chatRatioDeviation);

        if (chatRatioDeviation < 0.3) {
            flags.add(new SuspiciousFlag("low_chat_engagement",
                    String.format("Only %.1f%% of viewers chatted (expected %.0f-%.0f%%)",
                            chatViewerRatio * 100, expectedChatRatio * 50, expectedChatRatio * 150)));
        }

        // --- Signal 2: Message Quality (20%) ---
        MessageAnalysis ma = recap.messageAnalysis();
        double vocabularyDiversity = computeVocabularyDiversity(sessionId, recap.totalMessages());
        double emoteOnlyRatio = (ma != null && ma.shortMessageRatio() > 0) ? ma.shortMessageRatio() : 0;
        double repetitiveMessageRatio = computeRepetitiveMessageRatio(sessionId, recap.totalMessages());

        double qualitySignal = 0.4 * clampSignal(vocabularyDiversity * 2)
                + 0.3 * clampSignal(1.0 - emoteOnlyRatio)
                + 0.3 * clampSignal(1.0 - repetitiveMessageRatio);

        if (repetitiveMessageRatio > 0.4) {
            flags.add(new SuspiciousFlag("high_repetitive_messages",
                    String.format("%.0f%% of messages are repetitive", repetitiveMessageRatio * 100)));
        }

        // --- Signal 3: Chatter Behavior (25%) ---
        double singleMessageChatterRatio = computeSingleMessageChatterRatio(sessionId, recap.totalChatters());
        double timingUniformityScore = computeTimingUniformity(recap.chatActivity());

        double chatterSignal = 0.6 * clampSignal(1.0 - singleMessageChatterRatio)
                + 0.4 * clampSignal(1.0 - timingUniformityScore);

        if (singleMessageChatterRatio > 0.7) {
            flags.add(new SuspiciousFlag("high_single_message_chatters",
                    String.format("%.0f%% of chatters sent exactly 1 message", singleMessageChatterRatio * 100)));
        }

        // --- Signal 4: Engagement Authenticity (15%) ---
        double organicFlowScore = computeOrganicFlow(recap.chatActivity());
        double conversationDepthScore = computeConversationDepth(recap);

        double engagementSignal = 0.6 * organicFlowScore + 0.4 * conversationDepthScore;

        if (organicFlowScore < 0.3) {
            flags.add(new SuspiciousFlag("flat_chat_pattern",
                    "Chat activity suggests unusually uniform messaging pattern"));
        }

        // --- Signal 5: Cross-Session Consistency (15%) ---
        double viewerChatCorrelation = computeCrossSessionCorrelation(twitchId);
        double crossSessionSignal = clampSignal((viewerChatCorrelation + 1.0) / 2.0);

        if (viewerChatCorrelation < -0.3) {
            flags.add(new SuspiciousFlag("inverse_viewer_chat_correlation",
                    "Chat activity may decrease as viewer count increases, which may suggest inflated viewer counts"));
        }

        // --- Weighted final score ---
        double rawScore = W_CHAT_RATIO * chatRatioSignal
                + W_MESSAGE_QUALITY * qualitySignal
                + W_CHATTER_BEHAVIOR * chatterSignal
                + W_ENGAGEMENT * engagementSignal
                + W_CROSS_SESSION * crossSessionSignal;

        int authenticityScore = (int) Math.round(rawScore * 100);
        authenticityScore = Math.max(0, Math.min(100, authenticityScore));

        // --- Confidence level ---
        long durationMinutes = recap.endTime() != null
                ? Duration.between(recap.startTime(), recap.endTime()).toMinutes()
                : 0;
        int priorSessions = countPriorSessions(twitchId);
        String confidenceLevel = computeConfidence(recap.totalMessages(), recap.totalChatters(), durationMinutes, priorSessions);

        return new SessionAuthenticity(
                sessionId, twitchId, authenticityScore, confidenceLevel,
                chatViewerRatio, expectedChatRatio, chatRatioDeviation,
                vocabularyDiversity, emoteOnlyRatio, repetitiveMessageRatio,
                singleMessageChatterRatio, timingUniformityScore,
                organicFlowScore, conversationDepthScore,
                viewerChatCorrelation,
                flags, ALGORITHM_VERSION, Instant.now(), null
        );
    }

    private double computeExpectedChatRatio(Integer peakViewerCount) {
        if (peakViewerCount == null || peakViewerCount == 0) return 0.10;
        // Larger streams have lower chat participation rates
        if (peakViewerCount < 100) return 0.30;
        if (peakViewerCount < 1000) return 0.15;
        if (peakViewerCount < 10000) return 0.08;
        if (peakViewerCount < 50000) return 0.04;
        return 0.02;
    }

    private double computeVocabularyDiversity(long sessionId, long totalMessages) {
        if (totalMessages == 0) return 0;
        // Count distinct words used in this session
        var table = table(name("twitch", "message_word"));
        Long distinctWords = dsl.selectCount()
                .from(dsl.selectDistinct(field("word"))
                        .from(table)
                        .where(field("session_id").eq(sessionId)))
                .fetchOneInto(Long.class);

        if (distinctWords == null || distinctWords == 0) return 0;
        // Normalize: expect about 1 unique word per 5 messages in organic chat
        return Math.min(1.0, (double) distinctWords / (totalMessages * 0.2));
    }

    private double computeRepetitiveMessageRatio(long sessionId, long totalMessages) {
        if (totalMessages < 10) return 0;
        var msgTable = table(name("twitch", "message"));
        // Count messages that appear 3+ times verbatim
        Long repeatedCount = dsl.select(sum(field("cnt", Long.class)))
                .from(
                        dsl.select(count().as("cnt"))
                                .from(msgTable)
                                .where(field("session_id").eq(sessionId))
                                .groupBy(field("message_text"))
                                .having(count().ge(3))
                )
                .fetchOneInto(Long.class);

        return repeatedCount != null ? (double) repeatedCount / totalMessages : 0;
    }

    private double computeSingleMessageChatterRatio(long sessionId, long totalChatters) {
        if (totalChatters == 0) return 0;
        var msgTable = table(name("twitch", "message"));
        Long singleMsgChatters = dsl.selectCount()
                .from(
                        dsl.select(field("author"))
                                .from(msgTable)
                                .where(field("session_id").eq(sessionId))
                                .groupBy(field("author"))
                                .having(count().eq(1))
                )
                .fetchOneInto(Long.class);

        return singleMsgChatters != null ? (double) singleMsgChatters / totalChatters : 0;
    }

    private double computeTimingUniformity(List<ChatActivityBucket> chatActivity) {
        if (chatActivity == null || chatActivity.size() < 3) return 0;
        // Measure coefficient of variation of inter-bucket message counts
        double mean = chatActivity.stream()
                .mapToLong(ChatActivityBucket::messageCount)
                .average()
                .orElse(0);
        if (mean == 0) return 0;

        double variance = chatActivity.stream()
                .mapToDouble(b -> Math.pow(b.messageCount() - mean, 2))
                .average()
                .orElse(0);

        double cv = Math.sqrt(variance) / mean;
        // Low CV = uniform (suspicious), high CV = variable (organic)
        // Organic chat typically has CV > 0.5
        return cv < 0.5 ? 1.0 - (cv / 0.5) : 0;
    }

    private double computeOrganicFlow(List<ChatActivityBucket> chatActivity) {
        if (chatActivity == null || chatActivity.size() < 3) return 0.5;
        // Organic chat has natural peaks and valleys
        double mean = chatActivity.stream()
                .mapToLong(ChatActivityBucket::messageCount)
                .average()
                .orElse(0);
        if (mean == 0) return 0;

        double variance = chatActivity.stream()
                .mapToDouble(b -> Math.pow(b.messageCount() - mean, 2))
                .average()
                .orElse(0);

        double cv = Math.sqrt(variance) / mean;
        // Higher variance = more organic
        return Math.min(1.0, cv / 1.0);
    }

    private double computeConversationDepth(StreamRecap recap) {
        double score = 0;
        if (recap.messageAnalysis() != null) {
            // Questions suggest genuine engagement
            score += recap.messageAnalysis().questionRatio() * 3;
            // Longer messages suggest depth
            score += Math.min(1.0, recap.messageAnalysis().avgMessageLength() / 50.0) * 0.3;
        }
        return Math.min(1.0, score);
    }

    private double computeCrossSessionCorrelation(long twitchId) {
        // Pearson correlation between viewer count and chat participation across sessions
        var recapTable = table(name("twitch", "stream_recap"));
        Result<? extends Record2<?, ?>> rows = dsl.select(
                        field("peak_viewer_count", Double.class),
                        field("chat_participation_rate", Double.class)
                )
                .from(recapTable)
                .join(table(name("twitch", "session")))
                .on(field(name("twitch", "stream_recap", "session_id"))
                        .eq(field(name("twitch", "session", "id"))))
                .where(field(name("twitch", "session", "twitch_id")).eq(twitchId))
                .and(field("peak_viewer_count").isNotNull())
                .and(field("chat_participation_rate").isNotNull())
                .orderBy(field(name("twitch", "stream_recap", "session_id")).desc())
                .limit(30)
                .fetch();

        if (rows.size() < 3) return 0; // not enough data

        double[] viewers = new double[rows.size()];
        double[] chatRates = new double[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            viewers[i] = rows.get(i).get(0, Double.class);
            chatRates[i] = rows.get(i).get(1, Double.class);
        }

        return pearson(viewers, chatRates);
    }

    private double pearson(double[] x, double[] y) {
        int n = x.length;
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0, sumY2 = 0;
        for (int i = 0; i < n; i++) {
            sumX += x[i];
            sumY += y[i];
            sumXY += x[i] * y[i];
            sumX2 += x[i] * x[i];
            sumY2 += y[i] * y[i];
        }
        double denom = Math.sqrt((n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY));
        if (denom == 0) return 0;
        return (n * sumXY - sumX * sumY) / denom;
    }

    private int countPriorSessions(long twitchId) {
        var recapTable = table(name("twitch", "stream_recap"));
        var sessionTable = table(name("twitch", "session"));
        Long count = dsl.selectCount()
                .from(recapTable)
                .join(sessionTable)
                .on(field(name("twitch", "stream_recap", "session_id"))
                        .eq(field(name("twitch", "session", "id"))))
                .where(field(name("twitch", "session", "twitch_id")).eq(twitchId))
                .fetchOneInto(Long.class);
        return count != null ? count.intValue() : 0;
    }

    private String computeConfidence(long totalMessages, long totalChatters, long durationMinutes, int priorSessions) {
        if (totalMessages > 1000 && totalChatters > 50 && durationMinutes > 60 && priorSessions > 10) {
            return "high";
        }
        if (totalMessages > 100 && totalChatters > 10 && durationMinutes > 30) {
            return "medium";
        }
        return "low";
    }

    private long resolveTwitchId(long sessionId) {
        var sessionTable = table(name("twitch", "session"));
        Long id = dsl.select(field("twitch_id", Long.class))
                .from(sessionTable)
                .where(field("id").eq(sessionId))
                .fetchOneInto(Long.class);
        return id != null ? id : 0;
    }

    private double clampSignal(double value) {
        return Math.max(0, Math.min(1.0, value));
    }
}
