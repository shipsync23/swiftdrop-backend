package com.swiftdrop.auth;

import com.swiftdrop.auth.dto.AuthResponse;

/** Internal result of a successful login: the JSON body plus the raw refresh token for the cookie. */
public record AuthResult(AuthResponse body, String refreshToken) {
}
