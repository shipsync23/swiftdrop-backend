package com.swiftdrop.courier;

import com.swiftdrop.common.exception.ApiException;
import com.swiftdrop.events.EventPublisher;
import com.swiftdrop.events.ParcelStatusChangedEvent;
import com.swiftdrop.parcel.Parcel;
import com.swiftdrop.parcel.ParcelRepository;
import com.swiftdrop.parcel.ParcelStatus;
import com.swiftdrop.parcel.StatusHistory;
import com.swiftdrop.parcel.StatusHistoryRepository;
import com.swiftdrop.parcel.dto.ParcelResponse;
import com.swiftdrop.pricing.ParcelSize;
import com.swiftdrop.pricing.Zone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Courier status update rules (report 5.3, 6.5). */
class StatusServiceTest {

    private final ParcelRepository parcels = mock(ParcelRepository.class);
    private final StatusHistoryRepository history = mock(StatusHistoryRepository.class);
    private final CourierRepository couriers = mock(CourierRepository.class);
    private final AssignmentRepository assignments = mock(AssignmentRepository.class);
    private final DeliveryProofRepository proofs = mock(DeliveryProofRepository.class);
    private final PhotoStorage photoStorage = mock(PhotoStorage.class);
    private final EventPublisher events = mock(EventPublisher.class);

    private StatusService service;
    private Parcel parcel;
    private final UUID courierUserId = UUID.randomUUID();
    private final Courier courier = new Courier();

    @BeforeEach
    void setUp() {
        service = new StatusService(parcels, history, couriers, assignments, proofs, photoStorage, events);
        courier.setId(UUID.randomUUID());
        parcel = parcel(ParcelStatus.CREATED);
        when(parcels.findById(parcel.getId())).thenReturn(Optional.of(parcel));
        when(couriers.findByUserId(courierUserId)).thenReturn(Optional.of(courier));
    }

    @Test
    void courierCannotUpdateAParcelThatIsNotAssignedToThem() {
        when(assignments.existsByParcelIdAndCourierIdAndActiveTrue(parcel.getId(), courier.getId())).thenReturn(false);

        assertThatThrownBy(() -> service.updateByCourier(parcel.getId(), courierUserId, cmd(ParcelStatus.PICKED_UP)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(parcels, never()).saveAndFlush(any());
        verify(events, never()).publish(any());
    }

    @Test
    void skippingAStepIsRejectedWithConflict() {
        assigned();

        assertThatThrownBy(() -> service.updateByCourier(parcel.getId(), courierUserId, cmd(ParcelStatus.DELIVERED)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(events, never()).publish(any());
    }

    @Test
    void deliveredNeedsProof() {
        parcel.setStatus(ParcelStatus.OUT_FOR_DELIVERY);
        assigned();

        assertThatThrownBy(() -> service.updateByCourier(parcel.getId(), courierUserId, cmd(ParcelStatus.DELIVERED)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void failedNeedsAReason() {
        parcel.setStatus(ParcelStatus.OUT_FOR_DELIVERY);
        assigned();

        assertThatThrownBy(() -> service.updateByCourier(parcel.getId(), courierUserId, cmd(ParcelStatus.FAILED)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("reason");
    }

    @Test
    void outdatedScreenIsRejectedWithConflict() {
        assigned();
        StatusUpdateCommand stale = new StatusUpdateCommand(ParcelStatus.PICKED_UP, null, null, null, null, 99);

        assertThatThrownBy(() -> service.updateByCourier(parcel.getId(), courierUserId, stale))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void validUpdateSavesHistoryAndPublishesTheEvent() {
        assigned();

        ParcelResponse result = service.updateByCourier(parcel.getId(), courierUserId, cmd(ParcelStatus.PICKED_UP));

        assertThat(result.status()).isEqualTo("PICKED_UP");
        ArgumentCaptor<StatusHistory> historyRow = ArgumentCaptor.forClass(StatusHistory.class);
        verify(history).save(historyRow.capture());
        assertThat(historyRow.getValue().getOldStatus()).isEqualTo(ParcelStatus.CREATED);
        assertThat(historyRow.getValue().getChangedBy()).isEqualTo(courierUserId);

        ArgumentCaptor<ParcelStatusChangedEvent> event = ArgumentCaptor.forClass(ParcelStatusChangedEvent.class);
        verify(events).publish(event.capture());
        assertThat(event.getValue().oldStatus()).isEqualTo("CREATED");
        assertThat(event.getValue().newStatus()).isEqualTo("PICKED_UP");
        assertThat(event.getValue().toPayload()).doesNotContainKeys("pickupAddress", "dropoffAddress", "recipientPhone");
    }

    @Test
    void deliveredWithRecipientNameStoresProof() {
        parcel.setStatus(ParcelStatus.OUT_FOR_DELIVERY);
        assigned();
        StatusUpdateCommand delivered = new StatusUpdateCommand(ParcelStatus.DELIVERED, "At the gate", "Kumari", null, null, null);

        service.updateByCourier(parcel.getId(), courierUserId, delivered);

        ArgumentCaptor<DeliveryProof> proof = ArgumentCaptor.forClass(DeliveryProof.class);
        verify(proofs).save(proof.capture());
        assertThat(proof.getValue().getRecipientName()).isEqualTo("Kumari");
        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.DELIVERED);
    }

    private void assigned() {
        when(assignments.existsByParcelIdAndCourierIdAndActiveTrue(parcel.getId(), courier.getId())).thenReturn(true);
    }

    private static StatusUpdateCommand cmd(ParcelStatus status) {
        return new StatusUpdateCommand(status, null, null, null, null, null);
    }

    private static Parcel parcel(ParcelStatus status) {
        Zone zone = new Zone();
        zone.setId(3);
        zone.setName("Kandy");
        Parcel p = new Parcel();
        p.setId(UUID.randomUUID());
        p.setTrackingNumber("SDTEST1234");
        p.setCustomerId(UUID.randomUUID());
        p.setPickupAddress("Colombo 03");
        p.setDropoffAddress("Kandy");
        p.setDestZone(zone);
        p.setRecipientName("Kumari");
        p.setRecipientPhone("0771234567");
        p.setSize(ParcelSize.SMALL);
        p.setWeightKg(BigDecimal.ONE);
        p.setFee(new BigDecimal("500"));
        p.setStatus(status);
        p.setVersion(0);
        p.setCreatedAt(Instant.now());
        p.setUpdatedAt(Instant.now());
        return p;
    }
}
