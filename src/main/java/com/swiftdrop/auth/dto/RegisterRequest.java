package com.swiftdrop.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body of POST /api/v1/auth/register (customer self-registration). */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 255)
        String email,

        @NotBlank
        @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Password must contain letters and numbers")
        String password,

        @NotBlank @Size(max = 150)
        String fullName,

        @Pattern(regexp = "^0\\d{9}$", message = "Phone must be a 10-digit Sri Lankan number, e.g. 0712345678")
        String phone
) {
}
