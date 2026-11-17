package com.swiftdrop.common.security;

import com.swiftdrop.common.exception.ApiException;
import com.swiftdrop.user.RoleName;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * Reads the logged-in user from the validated JWT.
 * Services must always use these values (never an id sent by the client) for ownership checks,
 * e.g. "a courier can only see parcels where courier id = id from the token" (report 6.5).
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id(Jwt jwt) {
        if (jwt == null) {
            throw ApiException.unauthorized("Authentication required");
        }
        return UUID.fromString(jwt.getSubject());
    }

    public static RoleName role(Jwt jwt) {
        if (jwt == null) {
            throw ApiException.unauthorized("Authentication required");
        }
        return RoleName.valueOf(jwt.getClaimAsString("role"));
    }
}
