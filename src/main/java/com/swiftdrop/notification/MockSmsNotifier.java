package com.swiftdrop.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Mock SMS gateway (real SMS is out of scope, report 1.2). Logs a masked number. */
@Component
public class MockSmsNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(MockSmsNotifier.class);

    @Override
    public String channel() {
        return "SMS";
    }

    @Override
    public void send(String recipient, String subject, String text) {
        log.info("[MOCK SMS] to {}: {}", mask(recipient), text);
    }

    /** 0712345678 -> 071****678 (no full phone numbers in logs, report 6.4). */
    static String mask(String phone) {
        if (phone == null || phone.length() < 7) {
            return "****";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 3);
    }
}
