package com.swiftdrop.auth.dto;

/** Returned after login, register and refresh. The refresh token goes in an HttpOnly cookie, not here. */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {
}
