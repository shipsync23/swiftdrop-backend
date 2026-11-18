package com.swiftdrop.courier;

import com.swiftdrop.common.exception.ApiException;
import com.swiftdrop.common.security.CurrentUser;
import com.swiftdrop.parcel.Parcel;
import com.swiftdrop.parcel.ParcelRepository;
import com.swiftdrop.parcel.ParcelStatus;
import com.swiftdrop.parcel.dto.ParcelResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Courier endpoints (role COURIER only).
 *  GET  /api/v1/courier/deliveries                  my assigned deliveries
 *  POST /api/v1/courier/parcels/{id}/status         update status (multipart form, optional photo)
 */
@RestController
@RequestMapping("/api/v1/courier")
@PreAuthorize("hasRole('COURIER')")
public class CourierController {

    private final CourierRepository couriers;
    private final AssignmentRepository assignments;
    private final ParcelRepository parcels;
    private final StatusService statusService;

    public CourierController(CourierRepository couriers, AssignmentRepository assignments,
                             ParcelRepository parcels, StatusService statusService) {
        this.couriers = couriers;
        this.assignments = assignments;
        this.parcels = parcels;
        this.statusService = statusService;
    }

    /** includeCompleted=false (default) shows only unfinished deliveries. */
    @GetMapping("/deliveries")
    @Transactional(readOnly = true)
    public List<ParcelResponse> myDeliveries(@AuthenticationPrincipal Jwt jwt,
                                             @RequestParam(defaultValue = "false") boolean includeCompleted) {
        Courier courier = couriers.findByUserId(CurrentUser.id(jwt))
                .orElseThrow(() -> ApiException.forbidden("Courier profile not found"));
        List<UUID> ids = assignments.activeParcelIdsForCourier(courier.getId());
        return parcels.findAllById(ids).stream()
                .filter(p -> includeCompleted || !p.getStatus().isFinal())
                .sorted(Comparator.comparing(Parcel::getCreatedAt))
                .map(ParcelResponse::from)
                .toList();
    }

    @PostMapping(value = "/parcels/{id}/status", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ParcelResponse updateStatus(@AuthenticationPrincipal Jwt jwt,
                                       @PathVariable UUID id,
                                       @RequestParam ParcelStatus status,
                                       @RequestParam(required = false) String note,
                                       @RequestParam(required = false) String recipientName,
                                       @RequestParam(required = false) String failureReason,
                                       @RequestParam(required = false) Integer expectedVersion,
                                       @RequestPart(required = false) MultipartFile photo) {
        StatusUpdateCommand cmd = new StatusUpdateCommand(status, note, recipientName, failureReason,
                photo, expectedVersion);
        return statusService.updateByCourier(id, CurrentUser.id(jwt), cmd);
    }
}
