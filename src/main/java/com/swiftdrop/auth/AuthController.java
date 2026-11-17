package com.swiftdrop.auth;

import com.swiftdrop.auth.dto.AuthResponse;
import com.swiftdrop.auth.dto.LoginRequest;
import com.swiftdrop.auth.dto.RegisterRequest;
import com.swiftdrop.auth.dto.UserResponse;
import com.swiftdrop.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints.
 *  POST /api/v1/auth/register  customer sign-up
 *  POST /api/v1/auth/login     email + password login
 *  POST /api/v1/auth/refresh   new access token using the refresh cookie
 *  POST /api/v1/auth/logout    revoke the refresh token
 *  GET  /api/v1/auth/me        the logged-in user (needs a Bearer token)
 * Google login starts at GET /oauth2/authorization/google (handled by Spring Security).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookie refreshCookie;

    public AuthController(AuthService authService, RefreshCookie refreshCookie) {
        this.authService = authService;
        this.refreshCookie = refreshCookie;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return withCookie(HttpStatus.CREATED, authService.registerCustomer(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return withCookie(HttpStatus.OK, authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken) {
        return withCookie(HttpStatus.OK, authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.clear())
                .build();
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return authService.currentUser(CurrentUser.id(jwt));
    }

    private ResponseEntity<AuthResponse> withCookie(HttpStatus status, AuthResult result) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, refreshCookie.create(result.refreshToken()))
                .body(result.body());
    }
}
