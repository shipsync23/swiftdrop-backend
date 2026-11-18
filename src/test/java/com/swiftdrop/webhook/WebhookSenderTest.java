package com.swiftdrop.webhook;

import com.swiftdrop.common.security.SecretCipher;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** Webhook security (report 4.4): HMAC signature and encrypted secrets. */
class WebhookSenderTest {

    @Test
    void hmacMatchesTheStandardTestVector() {
        String signature = WebhookSender.hmacSha256("key",
                "The quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8));
        assertThat(signature).isEqualTo("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8");
    }

    @Test
    void webhookSecretIsEncryptedAndCanBeDecrypted() {
        SecretCipher cipher = new SecretCipher("test-key-material");
        String encrypted = cipher.encrypt("whsec_abc123");

        assertThat(encrypted).doesNotContain("whsec_abc123");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("whsec_abc123");
        assertThat(cipher.encrypt("whsec_abc123")).isNotEqualTo(encrypted);   // random IV each time
    }
}
