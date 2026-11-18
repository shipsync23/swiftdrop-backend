package com.swiftdrop.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ZoneRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 255) String description,
        Boolean active
) {
}
