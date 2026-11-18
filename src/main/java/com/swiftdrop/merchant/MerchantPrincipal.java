package com.swiftdrop.merchant;

import java.util.UUID;

/** The authenticated caller of the merchant API (set by ApiKeyAuthenticationFilter). */
public record MerchantPrincipal(UUID merchantId, String businessName) {
}
