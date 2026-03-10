package space.forloop.chatalytics.api.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.springframework.stereotype.Service;
import space.forloop.chatalytics.data.domain.ChannelBenchmark;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static org.jooq.impl.DSL.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class BenchmarkService {

    private final DSLContext dsl;

    public ChannelBenchmark computeBenchmark(long channelId) {
        // Step 1: Get all channels with their authenticity scores
        var authTable = table(name("chat", "channel_authenticity"));
        Result<? extends Record> allChannels = dsl.select(
                        field("channel_id", Long.class),
                        field("avg_authenticity_score", Double.class)
                )
                .from(authTable)
                .where(field("avg_authenticity_score").isNotNull())
                .fetch();

        if (allChannels.isEmpty()) {
            return buildEmptyBenchmark(channelId);
        }

        // Find the target channel's score
        Double targetScore = null;
        for (var row : allChannels) {
            if (row.get(field("channel_id", Long.class)).equals(channelId)) {
                targetScore = row.get(field("avg_authenticity_score", Double.class));
                break;
            }
        }

        if (targetScore == null) {
            return buildEmptyBenchmark(channelId);
        }
        final double finalTargetScore = targetScore;

        // Step 2: Compute global average
        double globalAvg = allChannels.stream()
                .mapToDouble(r -> r.get(field("avg_authenticity_score", Double.class)))
                .average()
                .orElse(0);

        // Step 3: Compute percentile rank (% of channels with LOWER scores)
        long channelsBelow = allChannels.stream()
                .filter(r -> r.get(field("avg_authenticity_score", Double.class)) < finalTargetScore)
                .count();
        int percentileRank = allChannels.size() <= 1 ? 50
                : (int) Math.round((double) channelsBelow / (allChannels.size() - 1) * 100);
        percentileRank = Math.max(0, Math.min(100, percentileRank));

        // Step 4: Get average peak viewers per channel from stream_recap + session
        Map<Long, Double> channelAvgPeakViewers = fetchChannelAvgPeakViewers();

        // Step 5: Determine target channel's tier
        Double targetAvgPeak = channelAvgPeakViewers.get(channelId);
        String viewerTier = determineTier(targetAvgPeak);

        // Step 6: Compute tier average
        Set<Long> allAuthChannelIds = allChannels.stream()
                .map(r -> r.get(field("channel_id", Long.class)))
                .collect(Collectors.toSet());

        Map<Long, String> channelTiers = new HashMap<>();
        for (long id : allAuthChannelIds) {
            channelTiers.put(id, determineTier(channelAvgPeakViewers.get(id)));
        }

        List<Double> tierScores = new ArrayList<>();
        for (var row : allChannels) {
            long id = row.get(field("channel_id", Long.class));
            if (viewerTier.equals(channelTiers.get(id))) {
                tierScores.add(row.get(field("avg_authenticity_score", Double.class)));
            }
        }

        double tierAvg = tierScores.stream().mapToDouble(d -> d).average().orElse(globalAvg);
        int channelsInTier = tierScores.size();

        // Step 7: Determine primary category
        String primaryCategory = fetchPrimaryCategory(channelId);

        // Step 8: Compute category average (if category is available)
        Double categoryAvgScore = null;
        int channelsInCategory = 0;

        if (primaryCategory != null) {
            // Get primary categories for all channels
            Map<Long, String> channelCategories = fetchAllPrimaryCategories(allAuthChannelIds);

            List<Double> categoryScores = new ArrayList<>();
            for (var row : allChannels) {
                long id = row.get(field("channel_id", Long.class));
                String cat = channelCategories.get(id);
                if (primaryCategory.equalsIgnoreCase(cat)) {
                    categoryScores.add(row.get(field("avg_authenticity_score", Double.class)));
                }
            }

            if (!categoryScores.isEmpty()) {
                categoryAvgScore = categoryScores.stream().mapToDouble(d -> d).average().orElse(0);
                channelsInCategory = categoryScores.size();
            }
        }

        return new ChannelBenchmark(
                channelId,
                percentileRank,
                viewerTier,
                Math.round(tierAvg * 10.0) / 10.0,
                Math.round(globalAvg * 10.0) / 10.0,
                channelsInTier,
                primaryCategory,
                categoryAvgScore != null ? Math.round(categoryAvgScore * 10.0) / 10.0 : null,
                channelsInCategory
        );
    }

    private Map<Long, Double> fetchChannelAvgPeakViewers() {
        var recapTable = table(name("chat", "stream_recap"));
        var sessionTable = table(name("chat", "session"));

        Result<? extends Record> rows = dsl.select(
                        field(name("chat", "session", "channel_id"), Long.class).as("channel_id"),
                        avg(field("peak_viewer_count", Double.class)).as("avg_peak")
                )
                .from(recapTable)
                .join(sessionTable)
                .on(field(name("chat", "stream_recap", "session_id"))
                        .eq(field(name("chat", "session", "id"))))
                .where(field("peak_viewer_count").isNotNull())
                .groupBy(field(name("chat", "session", "channel_id")))
                .fetch();

        Map<Long, Double> result = new HashMap<>();
        for (var row : rows) {
            Long id = row.get("channel_id", Long.class);
            Object avgVal = row.get("avg_peak");
            double avg = 0;
            if (avgVal instanceof BigDecimal bd) {
                avg = bd.doubleValue();
            } else if (avgVal instanceof Number n) {
                avg = n.doubleValue();
            }
            if (id != null) {
                result.put(id, avg);
            }
        }
        return result;
    }

    private String determineTier(Double avgPeakViewers) {
        if (avgPeakViewers == null) return "small";
        if (avgPeakViewers >= 50000) return "elite";
        if (avgPeakViewers >= 10000) return "mega";
        if (avgPeakViewers >= 2000) return "large";
        if (avgPeakViewers >= 500) return "mid";
        return "small";
    }

    private String fetchPrimaryCategory(long channelId) {
        var recapTable = table(name("chat", "stream_recap"));
        var sessionTable = table(name("chat", "session"));

        // Fetch game_segments JSONB from recent recaps for this channel
        Result<? extends Record> rows = dsl.select(field("game_segments"))
                .from(recapTable)
                .join(sessionTable)
                .on(field(name("chat", "stream_recap", "session_id"))
                        .eq(field(name("chat", "session", "id"))))
                .where(field(name("chat", "session", "channel_id")).eq(channelId))
                .and(field("game_segments").isNotNull())
                .orderBy(field(name("chat", "stream_recap", "session_id")).desc())
                .limit(30)
                .fetch();

        return extractMostCommonGame(rows);
    }

    private Map<Long, String> fetchAllPrimaryCategories(Set<Long> channelIds) {
        if (channelIds.isEmpty()) return Collections.emptyMap();

        var recapTable = table(name("chat", "stream_recap"));
        var sessionTable = table(name("chat", "session"));

        Result<? extends Record> rows = dsl.select(
                        field(name("chat", "session", "channel_id"), Long.class).as("channel_id"),
                        field("game_segments")
                )
                .from(recapTable)
                .join(sessionTable)
                .on(field(name("chat", "stream_recap", "session_id"))
                        .eq(field(name("chat", "session", "id"))))
                .where(field(name("chat", "session", "channel_id")).in(channelIds))
                .and(field("game_segments").isNotNull())
                .orderBy(field(name("chat", "stream_recap", "session_id")).desc())
                .fetch();

        // Group by channel_id, then find most common game per channel
        Map<Long, List<Record>> grouped = new HashMap<>();
        for (var row : rows) {
            Long id = row.get("channel_id", Long.class);
            if (id != null) {
                grouped.computeIfAbsent(id, k -> new ArrayList<>()).add(row);
            }
        }

        Map<Long, String> result = new HashMap<>();
        for (var entry : grouped.entrySet()) {
            String game = extractMostCommonGame(entry.getValue());
            if (game != null) {
                result.put(entry.getKey(), game);
            }
        }
        return result;
    }

    private String extractMostCommonGame(Iterable<? extends Record> rows) {
        Map<String, Integer> gameCounts = new HashMap<>();

        for (var row : rows) {
            Object segments = row.get("game_segments");
            if (segments == null) continue;

            String json = segments.toString();
            // Parse gameName values from the JSONB array
            // The JSONB format is: [{"gameName":"...", ...}, ...]
            parseGameNames(json, gameCounts);
        }

        if (gameCounts.isEmpty()) return null;

        return gameCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private void parseGameNames(String json, Map<String, Integer> gameCounts) {
        // Simple parser for "gameName":"value" patterns in JSONB
        String key = "\"gameName\"";
        int idx = 0;
        while ((idx = json.indexOf(key, idx)) >= 0) {
            idx += key.length();
            // Skip whitespace and colon
            while (idx < json.length() && (json.charAt(idx) == ':' || json.charAt(idx) == ' ')) {
                idx++;
            }
            if (idx >= json.length() || json.charAt(idx) != '"') continue;
            idx++; // skip opening quote
            int end = json.indexOf('"', idx);
            if (end < 0) break;
            String gameName = json.substring(idx, end);
            if (!gameName.isEmpty()) {
                gameCounts.merge(gameName, 1, Integer::sum);
            }
            idx = end + 1;
        }
    }

    private ChannelBenchmark buildEmptyBenchmark(long channelId) {
        return new ChannelBenchmark(
                channelId, 50, "small", 0, 0, 0, null, null, 0
        );
    }
}
