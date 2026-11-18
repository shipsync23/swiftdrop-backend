package com.swiftdrop.merchant;

import com.swiftdrop.merchant.dto.ApiOrderRequest;
import com.swiftdrop.parcel.dto.ParcelResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Machine-to-machine merchant API (versioned, report 5.2.4). Authenticated with X-API-Key.
 *  POST /api/v1/orders                   create a delivery order -> 201 + tracking number
 *  GET  /api/v1/orders/{trackingNumber}  status of one of my orders
 */
@RestController
@RequestMapping("/api/v1/orders")
public class MerchantOrderApiController {

    private final MerchantService merchantService;

    public MerchantOrderApiController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ParcelResponse create(@AuthenticationPrincipal MerchantPrincipal merchant,
                                 @Valid @RequestBody ApiOrderRequest request) {
        return merchantService.createOrder(merchant.merchantId(), request);
    }

    @GetMapping("/{trackingNumber}")
    public ParcelResponse get(@AuthenticationPrincipal MerchantPrincipal merchant,
                              @PathVariable String trackingNumber) {
        return merchantService.orderByTracking(merchant.merchantId(), trackingNumber);
    }
}
