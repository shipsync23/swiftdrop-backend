package com.swiftdrop.dispatch;

import com.swiftdrop.audit.AuditService;
import com.swiftdrop.common.exception.ApiException;
import com.swiftdrop.courier.Assignment;
import com.swiftdrop.courier.AssignmentRepository;
import com.swiftdrop.courier.Courier;
import com.swiftdrop.courier.CourierRepository;
import com.swiftdrop.parcel.Parcel;
import com.swiftdrop.parcel.ParcelRepository;
import com.swiftdrop.parcel.ParcelStatus;
import com.swiftdrop.parcel.dto.ParcelResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dispatcher work (report use cases "Assign parcel to courier", "Monitor courier workloads").
 * Assigning does NOT change the parcel status (report 5.3.3); only the courier moves the status.
 */
@Service
public class DispatchService {

    private final ParcelRepository parcels;
    private final CourierRepository couriers;
    private final AssignmentRepository assignments;
    private final AuditService audit;

    public DispatchService(ParcelRepository parcels, CourierRepository couriers,
                           AssignmentRepository assignments, AuditService audit) {
        this.parcels = parcels;
        this.couriers = couriers;
        this.assignments = assignments;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ParcelResponse> unassigned() {
        return parcels.findUnassigned().stream().map(ParcelResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ParcelResponse> allParcels(ParcelStatus status) {
        List<Parcel> list = status == null
                ? parcels.findAllByOrderByCreatedAtDesc()
                : parcels.findByStatusOrderByCreatedAtDesc(status);
        return list.stream().map(ParcelResponse::from).toList();
    }

    /** Every courier with the number of unfinished deliveries they hold. */
    @Transactional(readOnly = true)
    public List<CourierWorkload> workloads() {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : assignments.countActiveByCourier()) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return couriers.findAll().stream()
                .map(c -> new CourierWorkload(c.getId(), c.getUser().getFullName(), c.getUser().getPhone(),
                        c.getZone() == null ? null : c.getZone().getName(), c.getVehicleType(),
                        c.getAvailability(), c.getUser().isActive(), counts.getOrDefault(c.getId(), 0L)))
                .sorted((a, b) -> Long.compare(a.activeDeliveries(), b.activeDeliveries()))
                .toList();
    }

    /** Assign, or reassign if the parcel already has a courier (the old assignment is kept as history). */
    @Transactional
    public AssignmentResponse assign(UUID parcelId, UUID courierId, UUID dispatcherUserId) {
        Parcel parcel = parcels.findById(parcelId)
                .orElseThrow(() -> ApiException.notFound("Parcel not found"));
        if (parcel.getStatus().isFinal()) {
            throw ApiException.conflict("A " + parcel.getStatus() + " parcel cannot be assigned");
        }
        Courier courier = couriers.findById(courierId)
                .orElseThrow(() -> ApiException.notFound("Courier not found"));
        if (!courier.getUser().isActive()) {
            throw ApiException.badRequest("This courier account is disabled");
        }

        Assignment current = assignments.findByParcelIdAndActiveTrue(parcelId).orElse(null);
        if (current != null) {
            if (current.getCourier().getId().equals(courierId)) {
                throw ApiException.conflict("Parcel is already assigned to this courier");
            }
            current.setActive(false);
            assignments.saveAndFlush(current);   // free the "one active assignment" unique index first
            audit.record(dispatcherUserId, "PARCEL_REASSIGNED", "PARCEL", parcelId,
                    current.getCourier().getId().toString(), courierId.toString());
        }

        Assignment assignment = new Assignment();
        assignment.setParcelId(parcelId);
        assignment.setCourier(courier);
        assignment.setAssignedBy(dispatcherUserId);
        assignments.save(assignment);

        return new AssignmentResponse(parcel.getId(), parcel.getTrackingNumber(), courier.getId(),
                courier.getUser().getFullName(), current != null);
    }

    public record CourierWorkload(UUID courierId, String name, String phone, String zone, String vehicleType,
                                  String availability, boolean active, long activeDeliveries) {
    }

    public record AssignmentResponse(UUID parcelId, String trackingNumber, UUID courierId, String courierName,
                                     boolean reassigned) {
    }
}
