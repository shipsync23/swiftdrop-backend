package com.swiftdrop.events;

/**
 * Dependency inversion (report 7.1): business services depend on this interface,
 * not on RabbitMQ. In tests it can be replaced with a mock.
 */
public interface EventPublisher {

    void publish(ParcelStatusChangedEvent event);
}
