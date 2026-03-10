package space.forloop.chatalytics.api.tasks;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static space.forloop.chatalytics.data.generated.tables.Message.MESSAGE;
import static space.forloop.chatalytics.data.generated.tables.MessageWord.MESSAGE_WORD;
import static space.forloop.chatalytics.data.generated.tables.Stopwords.STOPWORDS;

@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncTokenizationTask {

    private static final int CHUNK_SIZE = 10_000;

    // Field reference for the new column (not yet in jOOQ generated code)
    private static final Field<Instant> TOKENIZED_AT = DSL.field("tokenized_at", Instant.class);

    private final DSLContext dsl;

    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    public void tokenizeMessages() {
        // Load stopwords once per run
        Set<String> stopwords = new HashSet<>(
                dsl.select(STOPWORDS.WORD).from(STOPWORDS).fetchInto(String.class)
        );

        // Fetch un-tokenized messages in chunks
        var messages = dsl.select(MESSAGE.ID, MESSAGE.CHANNEL_ID, MESSAGE.SESSION_ID, MESSAGE.MESSAGE_TEXT)
                .from(MESSAGE)
                .where(TOKENIZED_AT.isNull())
                .orderBy(MESSAGE.ID.asc())
                .limit(CHUNK_SIZE)
                .fetch();

        if (messages.isEmpty()) return;

        log.info("Tokenizing {} messages asynchronously", messages.size());

        // Build bulk word inserts
        var insert = dsl.insertInto(MESSAGE_WORD,
                MESSAGE_WORD.MESSAGE_ID, MESSAGE_WORD.CHANNEL_ID, MESSAGE_WORD.SESSION_ID, MESSAGE_WORD.WORD);

        int wordCount = 0;
        List<Long> processedIds = new ArrayList<>();

        for (var row : messages) {
            Long messageId = row.get(MESSAGE.ID);
            Long channelId = row.get(MESSAGE.CHANNEL_ID);
            Long sessionId = row.get(MESSAGE.SESSION_ID);
            String text = row.get(MESSAGE.MESSAGE_TEXT);
            processedIds.add(messageId);

            // Tokenize: lowercase, remove non-alpha, split by whitespace
            String cleaned = text.toLowerCase().replaceAll("[^a-zA-Z\\s]", "");
            String[] tokens = cleaned.split("\\s+");

            for (String token : tokens) {
                if (!token.isEmpty() && !stopwords.contains(token)) {
                    insert = insert.values(messageId, channelId, sessionId, token);
                    wordCount++;
                }
            }
        }

        // Execute bulk word insert
        if (wordCount > 0) {
            insert.execute();
        }

        // Mark messages as tokenized
        dsl.update(MESSAGE)
                .set(TOKENIZED_AT, Instant.now())
                .where(MESSAGE.ID.in(processedIds))
                .execute();

        log.info("Tokenized {} messages, inserted {} words", processedIds.size(), wordCount);
    }
}
