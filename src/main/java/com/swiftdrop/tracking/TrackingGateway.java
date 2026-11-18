package com.swiftdrop.tracking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Live tracking with Server-Sent Events (report 5.3.4 "Tracking updates").
 * Browsers subscribe per tracking number; each status event is pushed to every open page.
 * The parcel version is used to ignore events that arrive out of order.
 */
@Component
public class TrackingGateway {

    private static final Logger log = LoggerFactory.getLogger(TrackingGateway.class);
    private static final long TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

    private final Map<String, Set<SseEmitter>> subscribers = new ConcurrentHashMap<>();
    private final Map<String, Integer> lastVersion = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String trackingNumber, Object currentState) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        Set<SseEmitter> set = subscribers.computeIfAbsent(trackingNumber, k -> new CopyOnWriteArraySet<>());
        set.add(emitter);
        Runnable remove = () -> set.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        try {
            // Send the latest saved status first, so a reconnecting page is never stale.
            emitter.send(SseEmitter.event().name("status").data(currentState));
        } catch (IOException e) {
            remove.run();
        }
        return emitter;
    }

    public void push(String trackingNumber, int version, Object update) {
        Integer previous = lastVersion.get(trackingNumber);
        if (previous != null && version < previous) {
            return;   // outdated event
        }
        lastVersion.put(trackingNumber, version);
        Set<SseEmitter> set = subscribers.getOrDefault(trackingNumber, Set.of());
        for (SseEmitter emitter : set) {
            try {
                emitter.send(SseEmitter.event().name("status").data(update));
            } catch (IOException | IllegalStateException e) {
                set.remove(emitter);
            }
        }
        if (!set.isEmpty()) {
            log.debug("Pushed {} to {} live page(s)", trackingNumber, set.size());
        }
    }
}
