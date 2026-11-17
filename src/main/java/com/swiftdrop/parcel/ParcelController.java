package com.swiftdrop.parcel;

import com.swiftdrop.common.security.CurrentUser;
import com.swiftdrop.parcel.dto.CreateParcelRequest;
import com.swiftdrop.parcel.dto.EstimateRequest;
import com.swiftdrop.parcel.dto.FeeEstimateResponse;
import com.swiftdrop.parcel.dto.ParcelResponse;
import com.swiftdrop.parcel.dto.StatusHistoryResponse;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Customer parcel endpoints.
 *  POST /api/v1/parcels/estimate     fee before confirming
 *  POST /api/v1/parcels              create (confirm) a delivery request
 *  GET  /api/v1/parcels              my delivery history
 *  GET  /api/v1/parcels/{id}         one parcel
 *  GET  /api/v1/parcels/{id}/history status timeline
 *  GET  /api/v1/parcels/{id}/receipt PDF receipt
 */
@RestController
@RequestMapping("/api/v1/parcels")
public class ParcelController {

    private final ParcelService parcelService;
    private final ReceiptService receiptService;

    public ParcelController(ParcelService parcelService, ReceiptService receiptService) {
        this.parcelService = parcelService;
        this.receiptService = receiptService;
    }

    @PostMapping("/estimate")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'MERCHANT')")
    public FeeEstimateResponse estimate(@Valid @RequestBody EstimateRequest request) {
        return parcelService.estimate(request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    public ParcelResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateParcelRequest request) {
        return parcelService.createForCustomer(CurrentUser.id(jwt), request);
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<ParcelResponse> myParcels(@AuthenticationPrincipal Jwt jwt) {
        return parcelService.listForCustomer(CurrentUser.id(jwt));
    }

    @GetMapping("/{id}")
    public ParcelResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return parcelService.getForUser(id, CurrentUser.id(jwt), CurrentUser.role(jwt));
    }

    @GetMapping("/{id}/history")
    public List<StatusHistoryResponse> history(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return parcelService.historyForUser(id, CurrentUser.id(jwt), CurrentUser.role(jwt));
    }

    @GetMapping("/{id}/receipt")
    public ResponseEntity<byte[]> receipt(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        ReceiptService.Receipt receipt = receiptService.build(id, CurrentUser.id(jwt), CurrentUser.role(jwt));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(receipt.fileName()).build().toString())
                .body(receipt.content());
    }
}
