package com.swiftdrop.auth;

import com.swiftdrop.common.security.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Small endpoints that prove role-based access control works (useful in the demo and in tests).
 *  GET /api/v1/roles/admin    only ADMIN
 *  GET /api/v1/roles/staff    ADMIN or DISPATCHER
 *  GET /api/v1/roles/courier  only COURIER
 * Any other logged-in role gets 403, no token gets 401.
 */
@RestController
@RequestMapping("/api/v1/roles")
public class RoleCheckController {

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> admin(@AuthenticationPrincipal Jwt jwt) {
        return reply(jwt, "admin area");
    }

    @GetMapping("/staff")
    @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')")
    public Map<String, String> staff(@AuthenticationPrincipal Jwt jwt) {
        return reply(jwt, "staff area");
    }

    @GetMapping("/courier")
    @PreAuthorize("hasRole('COURIER')")
    public Map<String, String> courier(@AuthenticationPrincipal Jwt jwt) {
        return reply(jwt, "courier area");
    }

    private Map<String, String> reply(Jwt jwt, String area) {
        return Map.of("access", "granted", "area", area, "role", CurrentUser.role(jwt).name());
    }
}
