package com.swiftdrop.webhook;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * A pretend merchant e-commerce platform for the demo (report 8.1 "mock-merchant").
 * Set the merchant webhook URL to http://localhost:8080/mock-merchant/webhook
 * (add ?fail=true to make it return 500 and watch the retries).
 *  POST /mock-merchant/webhook    receives webhooks
 *  GET  /mock-merchant/received   last 50 webhooks received
 * If ?secret=whsec_... is added, the signature is checked and shown as valid / invalid.
 */
@RestController
@RequestMapping("/mock-merchant")
public class MockMerchantController {

    private final List<Map<String, Object>> received = Collections.synchronizedList(new LinkedList<>());

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, String>> receive(@RequestBody byte[] body,
                                                       @RequestHeader HttpHeaders headers,
                                                       @RequestParam(defaultValue = "false") boolean fail,
                                                       @RequestParam(required = false) String secret) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("receivedAt", Instant.now().toString());
        entry.put("eventId", headers.getFirst("X-SwiftDrop-Event-Id"));
        entry.put("attempt", headers.getFirst("X-SwiftDrop-Attempt"));
        String signature = headers.getFirst("X-SwiftDrop-Signature");
        entry.put("signature", signature);
        if (secret != null) {
            String expected = "sha256=" + WebhookSender.hmacSha256(secret, body);
            entry.put("signatureValid", expected.equals(signature));
        }
        entry.put("body", new String(body, java.nio.charset.StandardCharsets.UTF_8));
        entry.put("respondedWith", fail ? 500 : 200);
        received.add(0, entry);
        while (received.size() > 50) {
            received.remove(received.size() - 1);
        }
        return fail
                ? ResponseEntity.status(500).body(Map.of("status", "simulated failure"))
                : ResponseEntity.ok(Map.of("status", "received"));
    }

    @GetMapping("/received")
    public List<Map<String, Object>> received() {
        synchronized (received) {
            return new ArrayList<>(received);
        }
    }
}
