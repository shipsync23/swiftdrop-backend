package com.swiftdrop.admin;

import com.swiftdrop.admin.dto.CreateStaffRequest;
import com.swiftdrop.admin.dto.PricingRuleRequest;
import com.swiftdrop.admin.dto.ZoneRequest;
import com.swiftdrop.audit.AuditLog;
import com.swiftdrop.audit.AuditLogRepository;
import com.swiftdrop.common.security.CurrentUser;
import com.swiftdrop.user.RoleName;
import com.swiftdrop.user.UserStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Admin-only endpoints (report 6.3).
 *  Users:   GET /users, POST /users, PATCH /users/{id}/status, PATCH /users/{id}/role
 *  Zones:   GET /zones, POST /zones, PUT /zones/{id}
 *  Pricing: GET /pricing-rules?zoneId=, POST /pricing-rules, PUT /pricing-rules/{id}
 *  Reports: GET /reports/deliveries?from=2026-11-01&to=2026-11-30
 *  Audit:   GET /audit-logs
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminUserService userService;
    private final AdminCatalogService catalogService;
    private final ReportService reportService;
    private final AuditLogRepository auditLogs;

    public AdminController(AdminUserService userService, AdminCatalogService catalogService,
                           ReportService reportService, AuditLogRepository auditLogs) {
        this.userService = userService;
        this.catalogService = catalogService;
        this.reportService = reportService;
        this.auditLogs = auditLogs;
    }

    // ---------- users ----------

    @GetMapping("/users")
    public List<AdminUserService.AdminUserResponse> users() {
        return userService.list();
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserService.AdminUserResponse createStaff(@AuthenticationPrincipal Jwt jwt,
                                                          @Valid @RequestBody CreateStaffRequest request) {
        return userService.createStaff(request, CurrentUser.id(jwt));
    }

    @PatchMapping("/users/{id}/status")
    public AdminUserService.AdminUserResponse changeStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                           @Valid @RequestBody StatusChange request) {
        return userService.changeStatus(id, request.status(), CurrentUser.id(jwt));
    }

    @PatchMapping("/users/{id}/role")
    public AdminUserService.AdminUserResponse changeRole(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                         @Valid @RequestBody RoleChange request) {
        return userService.changeRole(id, request.role(), CurrentUser.id(jwt));
    }

    // ---------- zones ----------

    @GetMapping("/zones")
    public List<AdminCatalogService.ZoneView> zones() {
        return catalogService.listZones();
    }

    @PostMapping("/zones")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminCatalogService.ZoneView createZone(@AuthenticationPrincipal Jwt jwt,
                                                   @Valid @RequestBody ZoneRequest request) {
        return catalogService.createZone(request, CurrentUser.id(jwt));
    }

    @PutMapping("/zones/{id}")
    public AdminCatalogService.ZoneView updateZone(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id,
                                                   @Valid @RequestBody ZoneRequest request) {
        return catalogService.updateZone(id, request, CurrentUser.id(jwt));
    }

    // ---------- pricing ----------

    @GetMapping("/pricing-rules")
    public List<AdminCatalogService.PricingRuleView> pricingRules(@RequestParam(required = false) Integer zoneId) {
        return catalogService.listRules(zoneId);
    }

    @PostMapping("/pricing-rules")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminCatalogService.PricingRuleView createRule(@AuthenticationPrincipal Jwt jwt,
                                                          @Valid @RequestBody PricingRuleRequest request) {
        return catalogService.createRule(request, CurrentUser.id(jwt));
    }

    @PutMapping("/pricing-rules/{id}")
    public AdminCatalogService.PricingRuleView updateRule(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id,
                                                          @Valid @RequestBody PricingRuleRequest request) {
        return catalogService.updateRule(id, request, CurrentUser.id(jwt));
    }

    // ---------- reports & audit ----------

    /** Defaults to the last 7 days (Sri Lanka time) when no dates are given. */
    @GetMapping("/reports/deliveries")
    public ReportService.DeliveryReport deliveryReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Colombo"));
        LocalDate end = to != null ? to : today;
        LocalDate start = from != null ? from : end.minusDays(6);
        return reportService.deliveries(start, end);
    }

    @GetMapping("/audit-logs")
    public List<AuditLog> auditLogs() {
        return auditLogs.findTop100ByOrderByCreatedAtDesc();
    }

    public record StatusChange(@NotNull UserStatus status) {
    }

    public record RoleChange(@NotNull RoleName role) {
    }
}
