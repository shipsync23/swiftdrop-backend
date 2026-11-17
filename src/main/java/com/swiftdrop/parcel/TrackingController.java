package com.swiftdrop.parcel;

import com.swiftdrop.parcel.dto.PublicTrackingResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/v1/track/{trackingNumber} - public, no login (mobile-friendly tracking page). */
@RestController
@RequestMapping("/api/v1/track")
public class TrackingController {

    private final ParcelService parcelService;

    public TrackingController(ParcelService parcelService) {
        this.parcelService = parcelService;
    }

    @GetMapping("/{trackingNumber}")
    public PublicTrackingResponse track(@PathVariable String trackingNumber) {
        return parcelService.track(trackingNumber);
    }
}
