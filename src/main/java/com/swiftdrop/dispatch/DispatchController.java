package com.swiftdrop.dispatch;

import com.swiftdrop.common.security.CurrentUser;
import com.swiftdrop.parcel.ParcelStatus;
import com.swiftdrop.parcel.dto.ParcelResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Dispatcher endpoints (DISPATCHER or ADMIN - report 6.3 "Admin: all dispatcher actions").
 *  GET  /api/v1/dispatch/parcels/unassigned
 *  GET  /api/v1/dispatch/parcels?status=IN_TRANSIT
 *  GET  /api/v1/dispatch/couriers                 workloads
 *  POST /api/v1/dispatch/parcels/{id}/assign      { "courierId": "..." }
 */
@RestController
@RequestMapping("/api/v1/dispatch")
@PreAuthorize("hasAnyRole('DISPATCHER', 'ADMIN')")
public class DispatchController {

    private final DispatchService dispatchService;

    public DispatchController(DispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @GetMapping("/parcels/unassigned")
    public List<ParcelResponse> unassigned() {
        return dispatchService.unassigned();
    }

    @GetMapping("/parcels")
    public List<ParcelResponse> parcels(@RequestParam(required = false) ParcelStatus status) {
        return dispatchService.allParcels(status);
    }

    @GetMapping("/couriers")
    public List<DispatchService.CourierWorkload> couriers() {
        return dispatchService.workloads();
    }

    @PostMapping("/parcels/{id}/assign")
    public DispatchService.AssignmentResponse assign(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                     @Valid @RequestBody AssignRequest request) {
        return dispatchService.assign(id, request.courierId(), CurrentUser.id(jwt));
    }

    public record AssignRequest(@NotNull UUID courierId) {
    }
}
