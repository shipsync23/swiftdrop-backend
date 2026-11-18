package com.swiftdrop.merchant;

import com.swiftdrop.auth.AuthResult;
import com.swiftdrop.auth.RefreshCookie;
import com.swiftdrop.auth.dto.AuthResponse;
import com.swiftdrop.common.security.CurrentUser;
import com.swiftdrop.merchant.dto.MerchantRegisterRequest;
import com.swiftdrop.merchant.dto.MerchantResponse;
import com.swiftdrop.parcel.dto.ParcelResponse;
import com.swiftdrop.webhook.WebhookDelivery;
import com.swiftdrop.webhook.WebhookDeliveryRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Merchant web endpoints (JWT login, role MERCHANT) + public registration.
 *  POST   /api/v1/merchants/register         public, account starts PENDING
 *  GET    /api/v1/merchant/me                profile + approval status
 *  GET    /api/v1/merchant/orders            dashboard
 *  POST   /api/v1/merchant/api-keys          new key (approved only), shown once
 *  GET    /api/v1/merchant/api-keys
 *  DELETE /api/v1/merchant/api-keys/{id}     revoke
 *  PUT    /api/v1/merchant/webhook           { "url": "..." } -> new signing secret, shown once
 *  GET    /api/v1/merchant/webhook-deliveries  delivery log (attempts, results)
 */
@RestController
public class MerchantController {

    private final MerchantService merchantService;
    private final RefreshCookie refreshCookie;
    private final WebhookDeliveryRepository deliveries;

    public MerchantController(MerchantService merchantService, RefreshCookie refreshCookie,
                              WebhookDeliveryRepository deliveries) {
        this.merchantService = merchantService;
        this.refreshCookie = refreshCookie;
        this.deliveries = deliveries;
    }

    @PostMapping("/api/v1/merchants/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody MerchantRegisterRequest request) {
        AuthResult result = merchantService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, refreshCookie.create(result.refreshToken()))
                .body(result.body());
    }

    @GetMapping("/api/v1/merchant/me")
    @PreAuthorize("hasRole('MERCHANT')")
    public MerchantResponse me(@AuthenticationPrincipal Jwt jwt) {
        return merchantService.me(CurrentUser.id(jwt));
    }

    @GetMapping("/api/v1/merchant/orders")
    @PreAuthorize("hasRole('MERCHANT')")
    public List<ParcelResponse> orders(@AuthenticationPrincipal Jwt jwt) {
        return merchantService.ordersForDashboard(CurrentUser.id(jwt));
    }

    @PostMapping("/api/v1/merchant/api-keys")
    @PreAuthorize("hasRole('MERCHANT')")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public MerchantService.NewApiKey createKey(@AuthenticationPrincipal Jwt jwt) {
        return merchantService.createApiKey(CurrentUser.id(jwt));
    }

    @GetMapping("/api/v1/merchant/api-keys")
    @PreAuthorize("hasRole('MERCHANT')")
    public List<MerchantService.ApiKeyView> keys(@AuthenticationPrincipal Jwt jwt) {
        return merchantService.listApiKeys(CurrentUser.id(jwt));
    }

    @DeleteMapping("/api/v1/merchant/api-keys/{id}")
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<Void> revokeKey(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        merchantService.revokeApiKey(CurrentUser.id(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/v1/merchant/webhook")
    @PreAuthorize("hasRole('MERCHANT')")
    public MerchantService.WebhookSettings webhook(@AuthenticationPrincipal Jwt jwt,
                                                   @Valid @RequestBody WebhookRequest request) {
        return merchantService.configureWebhook(CurrentUser.id(jwt), request.url());
    }

    @GetMapping("/api/v1/merchant/webhook-deliveries")
    @PreAuthorize("hasRole('MERCHANT')")
    public List<WebhookDelivery> webhookDeliveries(@AuthenticationPrincipal Jwt jwt) {
        Merchant merchant = merchantService.byUser(CurrentUser.id(jwt));
        return deliveries.findTop100ByMerchantIdOrderByAttemptedAtDesc(merchant.getId());
    }

    public record WebhookRequest(@NotBlank @Size(max = 500) String url) {
    }
}
