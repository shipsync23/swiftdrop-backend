package com.swiftdrop.parcel;

import com.swiftdrop.parcel.dto.PublicTrackingResponse;
import com.swiftdrop.tracking.TrackingGateway;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Public tracking (no login, mobile-friendly page).
 *  GET /api/v1/track/{trackingNumber}       current status + history
 *  GET /api/v1/track/{trackingNumber}/live  Server-Sent Events stream of status changes
 * If the live connection drops, the page can fall back to polling the first endpoint.
 */
@RestController
@RequestMapping("/api/v1/track")
public class TrackingController {

    private final ParcelService parcelService;
    private final TrackingGateway gateway;

    public TrackingController(ParcelService parcelService, TrackingGateway gateway) {
        this.parcelService = parcelService;
        this.gateway = gateway;
    }

    @GetMapping("/{trackingNumber}")
    public PublicTrackingResponse track(@PathVariable String trackingNumber) {
        return parcelService.track(trackingNumber);
    }

    @GetMapping(value = "/{trackingNumber}/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter live(@PathVariable String trackingNumber) {
        PublicTrackingResponse current = parcelService.track(trackingNumber);   // 404 if unknown
        return gateway.subscribe(current.trackingNumber(), current);
    }
}
