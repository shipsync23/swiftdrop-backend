package com.swiftdrop.events;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the event to the outbox table. MANDATORY propagation: it must be called inside the
 * transaction that changes the parcel, so the parcel change and the event commit together.
 */
@Component
public class OutboxEventPublisher implements EventPublisher {

    private final OutboxEventRepository outbox;

    public OutboxEventPublisher(OutboxEventRepository outbox) {
        this.outbox = outbox;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(ParcelStatusChangedEvent event) {
        OutboxEvent row = new OutboxEvent();
        row.setAggregateId(event.parcelId());
        row.setEventType(ParcelStatusChangedEvent.TYPE);
        row.setPayload(event.toPayload());
        outbox.save(row);
    }
}
