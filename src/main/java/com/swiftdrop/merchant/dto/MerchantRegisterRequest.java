package com.swiftdrop.merchant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body of POST /api/v1/merchants/register (public). The account starts as PENDING. */
public record MerchantRegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Password must contain letters and numbers")
        String password,
        @NotBlank @Size(max = 150) String contactName,
        @Pattern(regexp = "^0\\d{9}$", message = "Phone must be a 10-digit Sri Lankan number") String phone,
        @NotBlank @Size(max = 150) String businessName,
        @Size(max = 50) String businessRegNo
) {
}
