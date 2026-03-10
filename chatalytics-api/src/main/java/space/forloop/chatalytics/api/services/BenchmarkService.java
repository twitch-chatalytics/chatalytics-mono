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

    public ChannelBenchmark computeBenchmark(long twitchId) {
        // Step 1: Get all channels with their authenticity scores
        var authTable = table(name("twitch", "channel_authenticity"));
        Result<? extends Record> allChannels = dsl.select(
                        field("twitch_id", Long.class),
                        field("avg_authenticity_score", Double.class)
                )
                .from(authTable)
                .where(field("avg_authenticity_score").isNotNull())
                .fetch();

        if (allChannels.isEmpty()) {
            return buildEmptyBenchmark(twitchId);
        }

        // Find the target channel's score
        Double targetScore = null;
        for (var row : allChannels) {
            if (row.get(field("twitch_id", Long.class)).equals(twitchId)) {
                targetScore = row.get(field("avg_authenticity_score", Double.class));
                break;
            }
        }

        if (targetScore == null) {
            return buildEmptyBenchmark(twitchId);
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
        Double targetAvgPeak = channelAvgPeakViewers.get(twitchId);
        String viewerTier = determineTier(targetAvgPeak);

        // Step 6: Compute tier average
        Set<Long> allAuthTwitchIds = allChannels.stream()
                .map(r -> r.get(field("twitch_id", Long.class)))
                .collect(Collectors.toSet());

        Map<Long, String> channelTiers = new HashMap<>();
        for (long id : allAuthTwitchIds) {
            channelTiers.put(id, determineTier(channelAvgPeakViewers.get(id)));
        }

        List<Double> tierScores = new ArrayList<>();
        for (var row : allChannels) {
            long id = row.get(field("twitch_id", Long.class));
            if (viewerTier.equals(channelTiers.get(id))) {
                tierScores.add(row.get(field("avg_authenticity_score", Double.class)));
            }
        }

        double tierAvg = tierScores.stream().mapToDouble(d -> d).average().orElse(globalAvg);
        int channelsInTier = tierScores.size();

        // Step 7: Determine primary category
        String primaryCategory = fetchPrimaryCategory(twitchId);

        // Step 8: Compute category average (if category is available)
        Double categoryAvgScore = null;
        int channelsInCategory = 0;

        if (primaryCategory != null) {
            // Get primary categories for all channels
            Map<Long, String> channelCategories = fetchAllPrimaryCategories(allAuthTwitchIds);

            List<Double> categoryScores = new ArrayList<>();
            for (var row : allChannels) {
                long id = row.get(field("twitch_id", Long.class));
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
                twitchId,
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
        var recapTable = table(name("twitch", "stream_recap"));
        var sessionTable = table(name("twitch", "session"));

        Result<? extends Record> rows = dsl.select(
                        field(name("twitch", "session", "twitch_id"), Long.class).as("twitch_id"),
                        avg(field("peak_viewer_count", Double.class)).as("avg_peak")
                )
                .from(recapTable)
                .join(sessionTable)
                .on(field(name("twitch", "stream_recap", "session_id"))
                        .eq(field(name("twitch", "session", "id"))))
                .where(field("peak_viewer_count").isNotNull())
                .groupBy(field(name("twitch", "session", "twitch_id")))
                .fetch();

        Map<Long, Double> result = new HashMap<>();
        for (var row : rows) {
            Long id = row.get("twitch_id", Long.class);
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

    private String fetchPrimaryCategory(long twitchId) {
        var recapTable = table(name("twitch", "stream_recap"));
        var sessionTable = table(name("twitch", "session"));

        // Fetch game_segments JSONB from recent recaps for this channel
        Result<? extends Record> rows = dsl.select(field("game_segments"))
                .from(recapTable)
                .join(sessionTable)
                .on(field(name("twitch", "stream_recap", "session_id"))
                        .eq(field(name("twitch", "session", "id"))))
                .where(field(name("twitch", "session", "twitch_id")).eq(twitchId))
                .and(field("game_segments").isNotNull())
                .orderBy(field(name("twitch", "stream_recap", "session_id")).desc())
                .limit(30)
                .fetch();

        return extractMostCommonGame(rows);
    }

    private Map<Long, String> fetchAllPrimaryCategories(Set<Long> twitchIds) {
        if (twitchIds.isEmpty()) return Collections.emptyMap();

        var recapTable = table(name("twitch", "stream_recap"));
        var sessionTable = table(name("twitch", "session"));

        Result<? extends Record> rows = dsl.select(
                        field(name("twitch", "session", "twitch_id"), Long.class).as("twitch_id"),
                        field("game_segments")
                )
                .from(recapTable)
                .join(sessionTable)
                .on(field(name("twitch", "stream_recap", "session_id"))
                        .eq(field(name("twitch", "session", "id"))))
                .where(field(name("twitch", "session", "twitch_id")).in(twitchIds))
                .and(field("game_segments").isNotNull())
                .orderBy(field(name("twitch", "stream_recap", "session_id")).desc())
                .fetch();

        // Group by twitch_id, then find most common game per channel
        Map<Long, List<Record>> grouped = new HashMap<>();
        for (var row : rows) {
            Long id = row.get("twitch_id", Long.class);
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

    private ChannelBenchmark buildEmptyBenchmark(long twitchId) {
        return new ChannelBenchmark(
                twitchId, 50, "small", 0, 0, 0, null, null, 0
        );
    }
}
