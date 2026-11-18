package com.swiftdrop.admin.dto;

import com.swiftdrop.user.RoleName;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Admin creates a courier, dispatcher or admin account (staff never self-register). */
public record CreateStaffRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 150) String fullName,
        @Pattern(regexp = "^0\\d{9}$", message = "Phone must be a 10-digit Sri Lankan number") String phone,
        @NotNull RoleName role,
        Integer zoneId,
        @Size(max = 30) String vehicleType,
        @Size(max = 20) String vehicleNumber
) {
}
