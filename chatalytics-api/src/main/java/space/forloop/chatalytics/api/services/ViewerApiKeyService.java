package space.forloop.chatalytics.api.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ViewerApiKeyService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    // In production, load from env/secret. Fallback for dev.
    private static final byte[] ENCRYPTION_KEY = System.getenv("VIEWER_KEY_ENCRYPTION_SECRET") != null
            ? System.getenv("VIEWER_KEY_ENCRYPTION_SECRET").getBytes(StandardCharsets.UTF_8)
            : "chatalytics-dev-key-32bytes!!!!!".getBytes(StandardCharsets.UTF_8);

    private final DSLContext dsl;

    public void storeKey(long viewerChannelId, String provider, String apiKey, String apiSecret) {
        String encryptedKey = encrypt(apiKey);
        String encryptedSecret = apiSecret != null ? encrypt(apiSecret) : null;

        dsl.insertInto(DSL.table("chat.viewer_api_key"))
                .set(DSL.field("viewer_channel_id", Long.class), viewerChannelId)
                .set(DSL.field("provider", String.class), provider)
                .set(DSL.field("encrypted_key", String.class), encryptedKey)
                .set(DSL.field("encrypted_secret", String.class), encryptedSecret)
                .set(DSL.field("updated_at", Instant.class), Instant.now())
                .onConflict(DSL.field("viewer_channel_id"), DSL.field("provider"))
                .doUpdate()
                .set(DSL.field("encrypted_key", String.class), encryptedKey)
                .set(DSL.field("encrypted_secret", String.class), encryptedSecret)
                .set(DSL.field("updated_at", Instant.class), Instant.now())
                .execute();

        log.info("Stored {} API key for viewer {}", provider, viewerChannelId);
    }

    public Optional<String[]> getKey(long viewerChannelId, String provider) {
        var record = dsl.select(
                        DSL.field("encrypted_key", String.class),
                        DSL.field("encrypted_secret", String.class))
                .from(DSL.table("chat.viewer_api_key"))
                .where(DSL.field("viewer_channel_id", Long.class).eq(viewerChannelId))
                .and(DSL.field("provider", String.class).eq(provider))
                .fetchOne();

        if (record == null) return Optional.empty();

        String key = decrypt(record.value1());
        String secret = record.value2() != null ? decrypt(record.value2()) : null;
        return Optional.of(new String[]{key, secret});
    }

    public void deleteKey(long viewerChannelId, String provider) {
        dsl.deleteFrom(DSL.table("chat.viewer_api_key"))
                .where(DSL.field("viewer_channel_id", Long.class).eq(viewerChannelId))
                .and(DSL.field("provider", String.class).eq(provider))
                .execute();

        log.info("Deleted {} API key for viewer {}", provider, viewerChannelId);
    }

    public boolean hasKey(long viewerChannelId, String provider) {
        return dsl.fetchCount(
                DSL.selectFrom(DSL.table("chat.viewer_api_key"))
                        .where(DSL.field("viewer_channel_id", Long.class).eq(viewerChannelId))
                        .and(DSL.field("provider", String.class).eq(provider))
        ) > 0;
    }

    private String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            SecretKeySpec keySpec = new SecretKeySpec(ENCRYPTION_KEY, 0, 32, "AES");
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Prepend IV to ciphertext
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    private String decrypt(String ciphertext) {
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);

            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, encrypted, 0, encrypted.length);

            SecretKeySpec keySpec = new SecretKeySpec(ENCRYPTION_KEY, 0, 32, "AES");
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
}
