package space.forloop.chatalytics.api.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.springframework.stereotype.Service;
import space.forloop.chatalytics.data.domain.ChannelBrandSafety;
import space.forloop.chatalytics.data.domain.ChannelBrandSafety.TopicCount;
import space.forloop.chatalytics.data.repositories.BrandSafetyRepository;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.jooq.impl.DSL.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class BrandSafetyService {

    private final DSLContext dsl;
    private final BrandSafetyRepository brandSafetyRepository;

    // Toxicity blocklist (common slurs, hate terms — kept minimal for starter set)
    private static final Set<String> TOXIC_TERMS = Set.of(
            "kys", "kill yourself", "neck yourself", "retard", "retarded",
            "faggot", "fag", "nigger", "nigga", "tranny", "kike",
            "chink", "spic", "wetback", "cunt"
    );

    private static final Set<String> POSITIVE_TERMS = Set.of(
            "love", "amazing", "awesome", "great", "pog", "poggers", "pogchamp",
            "gg", "nice", "hype", "lets go", "let's go", "hell yeah",
            "incredible", "insane", "clutch", "goated", "based", "w",
            "beautiful", "perfect", "goat", "legend", "legendary"
    );

    private static final Set<String> NEGATIVE_TERMS = Set.of(
            "hate", "trash", "garbage", "terrible", "awful", "worst",
            "boring", "cringe", "sucks", "bad", "horrible", "gross",
            "disgusting", "pathetic", "lame", "wack", "yikes", "dogwater"
    );

    private static final Pattern EMOTE_ONLY = Pattern.compile(
            "^[A-Z][a-zA-Z0-9]+$|^[a-z]+[A-Z]"
    );

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "is", "it", "to", "in", "and", "of", "for",
            "on", "at", "i", "you", "he", "she", "we", "they", "my", "your",
            "this", "that", "with", "but", "or", "not", "so", "do", "can",
            "just", "if", "be", "are", "was", "have", "has", "had", "will",
            "would", "could", "should", "been", "from", "up", "out", "all",
            "its", "im", "me", "no", "yes", "lol", "lmao", "ok", "yeah"
    );

    public void computeAndSave(long channelId) {
        // Fetch recent messages for this channel
        var messageTable = table(name("chat", "message"));
        var sessionTable = table(name("chat", "session"));

        Result<? extends Record> messages = dsl.select(
                        field("message_text", String.class)
                )
                .from(messageTable)
                .join(sessionTable)
                .on(field(name("chat", "message", "session_id"))
                        .eq(field(name("chat", "session", "id"))))
                .where(field(name("chat", "session", "channel_id")).eq(channelId))
                .orderBy(field(name("chat", "message", "id")).desc())
                .limit(10000)
                .fetch();

        if (messages.isEmpty()) {
            log.debug("No messages found for channelId {} — skipping brand safety", channelId);
            return;
        }

        int total = messages.size();
        int toxicCount = 0;
        int positiveCount = 0;
        int negativeCount = 0;
        int emoteOnlyCount = 0;
        int conversationalCount = 0; // messages > 20 chars that aren't commands
        Map<String, Integer> wordCounts = new HashMap<>();
        Map<String, Integer> langHints = new HashMap<>();

        for (var row : messages) {
            String text = row.get("message_text", String.class);
            if (text == null || text.isBlank()) continue;

            String lower = text.toLowerCase().trim();

            // Toxicity check
            for (String term : TOXIC_TERMS) {
                if (lower.contains(term)) {
                    toxicCount++;
                    break;
                }
            }

            // Sentiment check
            boolean isPositive = false;
            boolean isNegative = false;
            for (String term : POSITIVE_TERMS) {
                if (lower.contains(term)) {
                    isPositive = true;
                    break;
                }
            }
            if (!isPositive) {
                for (String term : NEGATIVE_TERMS) {
                    if (lower.contains(term)) {
                        isNegative = true;
                        break;
                    }
                }
            }
            if (isPositive) positiveCount++;
            else if (isNegative) negativeCount++;

            // Emote-only detection
            String trimmed = text.trim();
            if (EMOTE_ONLY.matcher(trimmed).matches() || trimmed.split("\\s+").length <= 2 && trimmed.length() <= 20) {
                emoteOnlyCount++;
            }

            // Conversational check (longer messages that aren't commands)
            if (lower.length() > 20 && !lower.startsWith("!") && !lower.startsWith("/")) {
                conversationalCount++;
            }

            // Topic extraction (word frequency)
            String[] words = lower.replaceAll("[^a-z0-9\\s]", "").split("\\s+");
            for (String word : words) {
                if (word.length() >= 4 && !STOP_WORDS.contains(word)) {
                    wordCounts.merge(word, 1, Integer::sum);
                }
            }

            // Language hint (very basic — detect non-ASCII blocks)
            if (containsCJK(text)) langHints.merge("CJK", 1, Integer::sum);
            else if (containsCyrillic(text)) langHints.merge("Cyrillic", 1, Integer::sum);
            else if (containsArabic(text)) langHints.merge("Arabic", 1, Integer::sum);
            else langHints.merge("Latin", 1, Integer::sum);
        }

        double toxicityRate = (double) toxicCount / total;
        double positiveRate = (double) positiveCount / total;
        double negativeRate = (double) negativeCount / total;
        double neutralRate = 1.0 - positiveRate - negativeRate;
        double emoteSpamRate = (double) emoteOnlyCount / total;
        double conversationRatio = (double) conversationalCount / total;

        // Brand safety score: 100 = perfectly safe, 0 = very toxic
        // Penalize for toxicity heavily, and slightly for extreme negativity
        int brandSafetyScore = (int) Math.round(
                Math.max(0, Math.min(100,
                        100 - (toxicityRate * 500) - (Math.max(0, negativeRate - 0.2) * 100)
                ))
        );

        // Top topics (top 10 words by frequency, excluding stop words)
        List<TopicCount> topTopics = wordCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .map(e -> new TopicCount(e.getKey(), e.getValue()))
                .toList();

        // Language distribution
        double langTotal = langHints.values().stream().mapToInt(i -> i).sum();
        Map<String, Double> languageDistribution = langHints.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> Math.round(e.getValue() / langTotal * 1000.0) / 1000.0));

        // Count sessions analyzed
        int sessionsAnalyzed = countSessions(channelId);

        ChannelBrandSafety safety = new ChannelBrandSafety(
                channelId,
                brandSafetyScore,
                round3(toxicityRate),
                round3(positiveRate),
                round3(negativeRate),
                round3(neutralRate),
                round3(emoteSpamRate),
                round3(conversationRatio),
                topTopics,
                languageDistribution,
                sessionsAnalyzed,
                Instant.now()
        );

        brandSafetyRepository.save(safety);
        log.info("Computed brand safety score {} for channelId {} ({} messages analyzed)",
                brandSafetyScore, channelId, total);
    }

    private int countSessions(long channelId) {
        var sessionTable = table(name("chat", "session"));
        return dsl.selectCount()
                .from(sessionTable)
                .where(field("channel_id").eq(channelId))
                .fetchOne(0, Integer.class);
    }

    private double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private boolean containsCJK(String s) {
        return s.codePoints().anyMatch(c -> c >= 0x4E00 && c <= 0x9FFF);
    }

    private boolean containsCyrillic(String s) {
        return s.codePoints().anyMatch(c -> c >= 0x0400 && c <= 0x04FF);
    }

    private boolean containsArabic(String s) {
        return s.codePoints().anyMatch(c -> c >= 0x0600 && c <= 0x06FF);
    }
}
