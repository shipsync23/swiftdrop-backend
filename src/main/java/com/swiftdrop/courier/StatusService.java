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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Courier status update (report 5.3). Checks, in order:
 *  1. the parcel exists                     -> 404
 *  2. it is assigned to THIS courier        -> 403
 *  3. the screen was not outdated (version) -> 409
 *  4. the status change is allowed          -> 409
 *  5. proof / failure reason is present     -> 400
 * Then parcel + history + proof + outbox event are saved in ONE transaction.
 * Email, webhooks and live tracking happen later, asynchronously (phase 6).
 */
@Service
public class StatusService {

    private final ParcelRepository parcels;
    private final StatusHistoryRepository history;
    private final CourierRepository couriers;
    private final AssignmentRepository assignments;
    private final DeliveryProofRepository proofs;
    private final PhotoStorage photoStorage;
    private final EventPublisher events;

    public StatusService(ParcelRepository parcels, StatusHistoryRepository history, CourierRepository couriers,
                         AssignmentRepository assignments, DeliveryProofRepository proofs,
                         PhotoStorage photoStorage, EventPublisher events) {
        this.parcels = parcels;
        this.history = history;
        this.couriers = couriers;
        this.assignments = assignments;
        this.proofs = proofs;
        this.photoStorage = photoStorage;
        this.events = events;
    }

    @Transactional
    public ParcelResponse updateByCourier(UUID parcelId, UUID courierUserId, StatusUpdateCommand cmd) {
        if (cmd.status() == null) {
            throw ApiException.badRequest("status is required");
        }
        Parcel parcel = parcels.findById(parcelId)
                .orElseThrow(() -> ApiException.notFound("Parcel not found"));
        Courier courier = couriers.findByUserId(courierUserId)
                .orElseThrow(() -> ApiException.forbidden("Courier profile not found"));

        // Authorization rule (report 6.5): the courier id always comes from the JWT, never from the request.
        if (!assignments.existsByParcelIdAndCourierIdAndActiveTrue(parcelId, courier.getId())) {
            throw ApiException.forbidden("This parcel is not assigned to you");
        }
        if (cmd.expectedVersion() != null && !cmd.expectedVersion().equals(parcel.getVersion())) {
            throw ApiException.conflict("This parcel was updated by someone else. Reload and try again");
        }

        ParcelStatus oldStatus = parcel.getStatus();
        ParcelStatus newStatus = cmd.status();
        if (!oldStatus.canMoveTo(newStatus)) {
            throw ApiException.conflict("Cannot change status from " + oldStatus + " to " + newStatus
                    + ". Allowed next: " + oldStatus.allowedNext());
        }
        validateProof(newStatus, cmd);

        parcel.setStatus(newStatus);
        parcels.saveAndFlush(parcel);   // version check happens here (optimistic locking)

        StatusHistory entry = new StatusHistory();
        entry.setParcelId(parcel.getId());
        entry.setOldStatus(oldStatus);
        entry.setNewStatus(newStatus);
        entry.setChangedBy(courierUserId);
        entry.setNote(trimToNull(cmd.note()));
        history.save(entry);

        if (newStatus == ParcelStatus.DELIVERED || newStatus == ParcelStatus.FAILED || cmd.photo() != null) {
            DeliveryProof proof = new DeliveryProof();
            proof.setParcelId(parcel.getId());
            proof.setStatusHistoryId(entry.getId());
            proof.setRecipientName(trimToNull(cmd.recipientName()));
            proof.setNote(trimToNull(cmd.note()));
            proof.setFailureReason(trimToNull(cmd.failureReason()));
            proof.setPhotoPath(photoStorage.save(cmd.photo()));
            proofs.save(proof);
        }

        events.publish(new ParcelStatusChangedEvent(UUID.randomUUID(), parcel.getId(), parcel.getTrackingNumber(),
                oldStatus.name(), newStatus.name(), courierUserId, parcel.getMerchantId(), parcel.getCustomerId(),
                Instant.now().toString(), parcel.getVersion()));

        return ParcelResponse.from(parcel);
    }

    private static void validateProof(ParcelStatus newStatus, StatusUpdateCommand cmd) {
        boolean hasPhoto = cmd.photo() != null && !cmd.photo().isEmpty();
        if (newStatus == ParcelStatus.DELIVERED && isBlank(cmd.recipientName()) && !hasPhoto) {
            throw ApiException.badRequest("A delivered parcel needs the recipient's name or a photo as proof");
        }
        if (newStatus == ParcelStatus.FAILED && isBlank(cmd.failureReason())) {
            throw ApiException.badRequest("A failure reason is required");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String trimToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }
}
