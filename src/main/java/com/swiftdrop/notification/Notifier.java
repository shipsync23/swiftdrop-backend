package com.swiftdrop.notification;

/**
 * One delivery channel. The real SMS gateway can replace MockSmsNotifier later
 * without changing the consumer (report 4.1 "provider sits behind a Notifier interface").
 */
public interface Notifier {

    String channel();

    void send(String recipient, String subject, String text);
}
