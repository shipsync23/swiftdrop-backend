package com.swiftdrop.messaging;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Idempotent consumers (report 4.4): delivery is at-least-once, so the same event may arrive twice.
 * processed_events has PRIMARY KEY (event_id, consumer_name). The insert runs in the same transaction
 * as the side effect; if the row already exists the event was handled before and is skipped.
 */
@Component
public class IdempotencyGuard {

    private final JdbcTemplate jdbc;

    public IdempotencyGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** True the first time this consumer sees the event (and records it). */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstTime(UUID eventId, String consumer) {
        int inserted = jdbc.update(
                "insert into processed_events (event_id, consumer_name) values (?, ?) on conflict do nothing",
                eventId, consumer);
        return inserted == 1;
    }

    public boolean alreadyProcessed(UUID eventId, String consumer) {
        Integer count = jdbc.queryForObject(
                "select count(*) from processed_events where event_id = ? and consumer_name = ?",
                Integer.class, eventId, consumer);
        return count != null && count > 0;
    }
}
