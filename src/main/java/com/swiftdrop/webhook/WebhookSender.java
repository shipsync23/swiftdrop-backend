package com.swiftdrop.webhook;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Sends one signed webhook. The X-SwiftDrop-Signature header is
 * "sha256=" + HMAC-SHA256(merchant secret, raw body), so the merchant can check it is genuine (report 4.4).
 * A non-2xx answer or no answer within 10 seconds counts as a failure.
 */
@Component
public class WebhookSender {

    private final RestClient client;

    public WebhookSender() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    public Result send(String url, String secret, byte[] body, UUID eventId, int attempt) {
        try {
            var response = client.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-SwiftDrop-Event", "ParcelStatusChanged")
                    .header("X-SwiftDrop-Event-Id", eventId.toString())
                    .header("X-SwiftDrop-Attempt", String.valueOf(attempt))
                    .header("X-SwiftDrop-Signature", "sha256=" + hmacSha256(secret, body))
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return new Result(true, response.getStatusCode().value(), null);
        } catch (RestClientResponseException e) {
            return new Result(false, e.getStatusCode().value(), "Merchant returned HTTP " + e.getStatusCode().value());
        } catch (Exception e) {
            return new Result(false, null, shorten(e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    public static String hmacSha256(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String shorten(String s) {
        return s == null ? null : (s.length() > 450 ? s.substring(0, 450) : s);
    }

    public record Result(boolean success, Integer httpStatus, String error) {
    }
}
