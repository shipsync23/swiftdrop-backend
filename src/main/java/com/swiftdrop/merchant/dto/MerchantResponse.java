package com.swiftdrop.merchant.dto;

import com.swiftdrop.merchant.Merchant;

import java.time.Instant;
import java.util.UUID;

public record MerchantResponse(
        UUID id,
        String businessName,
        String businessRegNo,
        String contactName,
        String email,
        String phone,
        String approvalStatus,
        String webhookUrl,
        Instant createdAt,
        Instant approvedAt
) {
    public static MerchantResponse from(Merchant m) {
        return new MerchantResponse(m.getId(), m.getBusinessName(), m.getBusinessRegNo(),
                m.getUser().getFullName(), m.getUser().getEmail(), m.getUser().getPhone(),
                m.getApprovalStatus().name(), m.getWebhookUrl(), m.getCreatedAt(), m.getApprovedAt());
    }
}
