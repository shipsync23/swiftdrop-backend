package com.swiftdrop.merchant;

import com.swiftdrop.common.security.CurrentUser;
import com.swiftdrop.merchant.dto.MerchantResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Admin use case "Approve merchant registration".
 *  GET  /api/v1/admin/merchants?status=PENDING
 *  POST /api/v1/admin/merchants/{id}/approve
 *  POST /api/v1/admin/merchants/{id}/reject
 */
@RestController
@RequestMapping("/api/v1/admin/merchants")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMerchantController {

    private final MerchantService merchantService;

    public AdminMerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @GetMapping
    public List<MerchantResponse> list(@RequestParam(required = false) ApprovalStatus status) {
        return merchantService.listForAdmin(status);
    }

    @PostMapping("/{id}/approve")
    public MerchantResponse approve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return merchantService.decide(id, true, CurrentUser.id(jwt));
    }

    @PostMapping("/{id}/reject")
    public MerchantResponse reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return merchantService.decide(id, false, CurrentUser.id(jwt));
    }
}
