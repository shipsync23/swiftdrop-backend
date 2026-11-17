package com.swiftdrop.parcel;

import com.swiftdrop.common.exception.ApiException;
import com.swiftdrop.events.EventPublisher;
import com.swiftdrop.events.ParcelStatusChangedEvent;
import com.swiftdrop.parcel.dto.CreateParcelRequest;
import com.swiftdrop.parcel.dto.EstimateRequest;
import com.swiftdrop.parcel.dto.FeeEstimateResponse;
import com.swiftdrop.parcel.dto.ParcelResponse;
import com.swiftdrop.parcel.dto.PublicTrackingResponse;
import com.swiftdrop.parcel.dto.StatusHistoryResponse;
import com.swiftdrop.pricing.ParcelSize;
import com.swiftdrop.pricing.PricingService;
import com.swiftdrop.pricing.PricingService.FeeQuote;
import com.swiftdrop.user.RoleName;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Parcel creation, lookup and tracking (report 5.1). */
@Service
public class ParcelService {

    private final ParcelRepository parcels;
    private final StatusHistoryRepository history;
    private final PricingService pricing;
    private final TrackingNumberGenerator trackingNumbers;
    private final EventPublisher events;

    public ParcelService(ParcelRepository parcels, StatusHistoryRepository history, PricingService pricing,
                         TrackingNumberGenerator trackingNumbers, EventPublisher events) {
        this.parcels = parcels;
        this.history = history;
        this.pricing = pricing;
        this.trackingNumbers = trackingNumbers;
        this.events = events;
    }

    /** Step 1 of booking: show the fee before the customer confirms. Nothing is saved. */
    @Transactional(readOnly = true)
    public FeeEstimateResponse estimate(EstimateRequest request) {
        FeeQuote quote = pricing.quote(request.destZoneId(), request.weightKg(), request.size());
        return toEstimate(quote);
    }

    /** Step 2 of booking: the customer confirmed the fee. */
    @Transactional
    public ParcelResponse createForCustomer(UUID customerId, CreateParcelRequest request) {
        FeeQuote quote = pricing.quote(request.destZoneId(), request.weightKg(), request.size());

        // Recheck: if pricing changed after the estimate, ask the customer to confirm again.
        if (quote.fee().compareTo(request.expectedFee()) != 0) {
            throw ApiException.conflict("The delivery fee has changed to LKR " + quote.fee()
                    + ". Please review and confirm again");
        }

        ParcelDraft draft = new ParcelDraft(request.pickupAddress(), request.dropoffAddress(),
                request.recipientName(), request.recipientPhone(), request.size(), request.weightKg());
        return ParcelResponse.from(create(draft, quote, customerId, null, customerId));
    }

    /**
     * Shared creation logic for customer bookings and merchant API orders (DRY).
     * Parcel, first history row and the CREATED event are saved in ONE transaction.
     */
    @Transactional
    public Parcel create(ParcelDraft draft, FeeQuote quote, UUID customerId, UUID merchantId, UUID createdBy) {
        Parcel parcel = new Parcel();
        parcel.setTrackingNumber(trackingNumbers.next());
        parcel.setCustomerId(customerId);
        parcel.setMerchantId(merchantId);
        parcel.setPickupAddress(draft.pickupAddress().trim());
        parcel.setDropoffAddress(draft.dropoffAddress().trim());
        parcel.setDestZone(quote.zone());
        parcel.setRecipientName(draft.recipientName().trim());
        parcel.setRecipientPhone(draft.recipientPhone());
        parcel.setSize(draft.size());
        parcel.setWeightKg(draft.weightKg());
        parcel.setFee(quote.fee());
        parcel.setStatus(ParcelStatus.CREATED);
        parcels.saveAndFlush(parcel);   // flush so id, version and timestamps are available

        StatusHistory first = new StatusHistory();
        first.setParcelId(parcel.getId());
        first.setOldStatus(null);
        first.setNewStatus(ParcelStatus.CREATED);
        first.setChangedBy(createdBy);
        first.setNote("Parcel created");
        history.save(first);

        events.publish(new ParcelStatusChangedEvent(UUID.randomUUID(), parcel.getId(), parcel.getTrackingNumber(),
                null, ParcelStatus.CREATED.name(), createdBy, merchantId, customerId,
                Instant.now().toString(), parcel.getVersion()));
        return parcel;
    }

    @Transactional(readOnly = true)
    public List<ParcelResponse> listForCustomer(UUID customerId) {
        return parcels.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(ParcelResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ParcelResponse getForUser(UUID parcelId, UUID userId, RoleName role) {
        return ParcelResponse.from(loadVisible(parcelId, userId, role));
    }

    @Transactional(readOnly = true)
    public List<StatusHistoryResponse> historyForUser(UUID parcelId, UUID userId, RoleName role) {
        Parcel parcel = loadVisible(parcelId, userId, role);
        return history.findByParcelIdOrderByChangedAtAsc(parcel.getId()).stream()
                .map(StatusHistoryResponse::from)
                .toList();
    }

    /** Public tracking page: anyone with the tracking number, status information only. */
    @Transactional(readOnly = true)
    public PublicTrackingResponse track(String trackingNumber) {
        String normalised = trackingNumber == null ? "" : trackingNumber.trim().toUpperCase(Locale.ROOT);
        Parcel parcel = parcels.findByTrackingNumber(normalised)
                .orElseThrow(() -> ApiException.notFound("No parcel found with this tracking number"));
        List<PublicTrackingResponse.Step> steps = history.findByParcelIdOrderByChangedAtAsc(parcel.getId()).stream()
                .map(h -> new PublicTrackingResponse.Step(h.getNewStatus().name(), h.getChangedAt()))
                .toList();
        return new PublicTrackingResponse(parcel.getTrackingNumber(), parcel.getStatus().name(),
                parcel.getDestZone().getName(), parcel.getUpdatedAt(), steps);
    }

    /**
     * Who may see a parcel's full details (report 2.6 / 6.5):
     * the customer who created it, or staff (dispatcher, admin).
     * Couriers and merchants get their own rules in later phases.
     */
    Parcel loadVisible(UUID parcelId, UUID userId, RoleName role) {
        Parcel parcel = parcels.findById(parcelId)
                .orElseThrow(() -> ApiException.notFound("Parcel not found"));
        boolean allowed = switch (role) {
            case ADMIN, DISPATCHER -> true;
            case CUSTOMER -> userId.equals(parcel.getCustomerId());
            default -> false;
        };
        if (!allowed) {
            throw ApiException.forbidden("You do not have access to this parcel");
        }
        return parcel;
    }

    private static FeeEstimateResponse toEstimate(FeeQuote quote) {
        return new FeeEstimateResponse(quote.fee(), "LKR", quote.zone().getId(),
                quote.zone().getName(), quote.strategy());
    }

    /** The delivery details shared by customer bookings and merchant orders. */
    public record ParcelDraft(String pickupAddress, String dropoffAddress, String recipientName,
                              String recipientPhone, ParcelSize size, BigDecimal weightKg) {
    }
}
