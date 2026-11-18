package com.swiftdrop.notification;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Factory pattern (report 7.2): returns the Notifier for a channel; callers never see concrete classes. */
@Component
public class NotificationFactory {

    private final Map<String, Notifier> byChannel;

    public NotificationFactory(List<Notifier> notifiers) {
        this.byChannel = notifiers.stream().collect(Collectors.toMap(Notifier::channel, Function.identity()));
    }

    public Notifier forChannel(String channel) {
        Notifier notifier = byChannel.get(channel);
        if (notifier == null) {
            throw new IllegalArgumentException("No notifier for channel " + channel);
        }
        return notifier;
    }
}
