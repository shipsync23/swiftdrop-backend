package com.swiftdrop.auth.dto;

import com.swiftdrop.user.User;

import java.util.UUID;

/** What the API returns about a user. The password hash and Google id never leave the server. */
public record UserResponse(
        UUID id,
        String email,
        String fullName,
        String phone,
        String role
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(),
                user.getPhone(), user.roleName().name());
    }
}
