package com.swiftdrop.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * The refresh token lives in an HttpOnly, SameSite cookie (report 6.2), so JavaScript
 * in the browser can never read it. It is only sent to /api/v1/auth/* endpoints.
 */
@Component
public class RefreshCookie {

    public static final String NAME = "sd_refresh";
    private static final String PATH = "/api/v1/auth";

    private final boolean secure;
    private final Duration maxAge;

    public RefreshCookie(@Value("${swiftdrop.cookie-secure}") boolean secure,
                         @Value("${swiftdrop.jwt.refresh-token-days}") long refreshTokenDays) {
        this.secure = secure;
        this.maxAge = Duration.ofDays(refreshTokenDays);
    }

    public String create(String rawToken) {
        return build(rawToken, maxAge).toString();
    }

    public String clear() {
        return build("", Duration.ZERO).toString();
    }

    public void write(HttpServletResponse response, String rawToken) {
        response.addHeader(HttpHeaders.SET_COOKIE, create(rawToken));
    }

    private ResponseCookie build(String value, Duration age) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(PATH)
                .maxAge(age)
                .build();
    }
}
