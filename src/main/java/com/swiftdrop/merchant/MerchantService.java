package com.swiftdrop.merchant;

import com.swiftdrop.audit.AuditService;
import com.swiftdrop.auth.AuthResult;
import com.swiftdrop.auth.AuthService;
import com.swiftdrop.common.exception.ApiException;
import com.swiftdrop.common.security.SecretCipher;
import com.swiftdrop.common.security.TokenHasher;
import com.swiftdrop.merchant.dto.ApiOrderRequest;
import com.swiftdrop.merchant.dto.MerchantRegisterRequest;
import com.swiftdrop.merchant.dto.MerchantResponse;
import com.swiftdrop.parcel.ParcelRepository;
import com.swiftdrop.parcel.ParcelService;
import com.swiftdrop.parcel.dto.ParcelResponse;
import com.swiftdrop.pricing.PricingService;
import com.swiftdrop.user.Role;
import com.swiftdrop.user.RoleName;
import com.swiftdrop.user.RoleRepository;
import com.swiftdrop.user.User;
import com.swiftdrop.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Merchant use cases (report 2.3): register, approval, API keys, webhook settings, orders. */
@Service
public class MerchantService {

    private final MerchantRepository merchants;
    private final ApiCredentialRepository credentials;
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final ParcelService parcelService;
    private final ParcelRepository parcels;
    private final PricingService pricing;
    private final SecretCipher cipher;
    private final AuditService audit;

    public MerchantService(MerchantRepository merchants, ApiCredentialRepository credentials, UserRepository users,
                           RoleRepository roles, PasswordEncoder passwordEncoder, AuthService authService,
                           ParcelService parcelService, ParcelRepository parcels, PricingService pricing,
                           SecretCipher cipher, AuditService audit) {
        this.merchants = merchants;
        this.credentials = credentials;
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.parcelService = parcelService;
        this.parcels = parcels;
        this.pricing = pricing;
        this.cipher = cipher;
        this.audit = audit;
    }

    // ---------- registration & approval ----------

    /** Creates the MERCHANT user and a PENDING merchant profile, and logs the merchant in. */
    @Transactional
    public AuthResult register(MerchantRegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.contactName().trim());
        user.setPhone(request.phone());
        user.setRole(role(RoleName.MERCHANT));
        users.save(user);

        Merchant merchant = new Merchant();
        merchant.setUser(user);
        merchant.setBusinessName(request.businessName().trim());
        merchant.setBusinessRegNo(request.businessRegNo());
        merchants.save(merchant);

        return authService.issueTokensFor(user);
    }

    @Transactional(readOnly = true)
    public MerchantResponse me(UUID userId) {
        return MerchantResponse.from(byUser(userId));
    }

    @Transactional(readOnly = true)
    public List<MerchantResponse> listForAdmin(ApprovalStatus status) {
        List<Merchant> list = status == null
                ? merchants.findAllByOrderByCreatedAtDesc()
                : merchants.findByApprovalStatusOrderByCreatedAtAsc(status);
        return list.stream().map(MerchantResponse::from).toList();
    }

    @Transactional
    public MerchantResponse decide(UUID merchantId, boolean approve, UUID adminId) {
        Merchant merchant = merchants.findById(merchantId)
                .orElseThrow(() -> ApiException.notFound("Merchant not found"));
        ApprovalStatus old = merchant.getApprovalStatus();
        ApprovalStatus next = approve ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED;
        merchant.setApprovalStatus(next);
        merchant.setApprovedBy(adminId);
        merchant.setApprovedAt(Instant.now());
        audit.record(adminId, approve ? "MERCHANT_APPROVED" : "MERCHANT_REJECTED", "MERCHANT",
                merchant.getId(), old.name(), next.name());
        return MerchantResponse.from(merchant);
    }

    // ---------- API keys ----------

    /** Returns the full key ONCE. Only approved merchants can obtain credentials (report 2.6). */
    @Transactional
    public NewApiKey createApiKey(UUID userId) {
        Merchant merchant = approvedByUser(userId);
        String rawKey = "sd_live_" + TokenHasher.randomToken();
        ApiCredential credential = new ApiCredential();
        credential.setMerchant(merchant);
        credential.setKeyPrefix(rawKey.substring(0, 12));
        credential.setKeyHash(TokenHasher.sha256(rawKey));
        credentials.save(credential);
        return new NewApiKey(credential.getId(), rawKey, credential.getKeyPrefix(),
                "Store this key now. It will not be shown again.");
    }

    @Transactional(readOnly = true)
    public List<ApiKeyView> listApiKeys(UUID userId) {
        Merchant merchant = byUser(userId);
        return credentials.findByMerchantIdOrderByCreatedAtDesc(merchant.getId()).stream()
                .map(c -> new ApiKeyView(c.getId(), c.getKeyPrefix() + "...", c.getStatus(),
                        c.getCreatedAt(), c.getRevokedAt()))
                .toList();
    }

    @Transactional
    public void revokeApiKey(UUID userId, UUID keyId) {
        Merchant merchant = byUser(userId);
        ApiCredential credential = credentials.findById(keyId)
                .filter(c -> c.getMerchant().getId().equals(merchant.getId()))
                .orElseThrow(() -> ApiException.notFound("API key not found"));
        credential.setStatus("REVOKED");
        credential.setRevokedAt(Instant.now());
    }

    // ---------- webhook settings ----------

    /** Saves the webhook URL and creates a new signing secret, returned ONCE. */
    @Transactional
    public WebhookSettings configureWebhook(UUID userId, String url) {
        Merchant merchant = approvedByUser(userId);
        validateUrl(url);
        String secret = "whsec_" + TokenHasher.randomToken();
        merchant.setWebhookUrl(url.trim());
        merchant.setWebhookSecretEnc(cipher.encrypt(secret));
        return new WebhookSettings(merchant.getWebhookUrl(), secret,
                "Verify the X-SwiftDrop-Signature header: sha256=HMAC_SHA256(secret, raw body). "
                        + "Store this secret now. It will not be shown again.");
    }

    // ---------- orders ----------

    /** Merchant API order creation (report 5.2). The merchant id comes from the API key, never the body. */
    @Transactional
    public ParcelResponse createOrder(UUID merchantId, ApiOrderRequest request) {
        Merchant merchant = merchants.findById(merchantId)
                .orElseThrow(() -> ApiException.unauthorized("Invalid API key"));
        PricingService.FeeQuote quote = pricing.quote(request.destZoneId(), request.weightKg(), request.size());
        ParcelService.ParcelDraft draft = new ParcelService.ParcelDraft(request.pickupAddress(),
                request.dropoffAddress(), request.recipientName(), request.recipientPhone(),
                request.size(), request.weightKg());
        return ParcelResponse.from(parcelService.create(draft, quote, null, merchant.getId(),
                merchant.getUser().getId()));
    }

    @Transactional(readOnly = true)
    public ParcelResponse orderByTracking(UUID merchantId, String trackingNumber) {
        return parcels.findByTrackingNumber(trackingNumber.trim().toUpperCase(Locale.ROOT))
                .filter(p -> merchantId.equals(p.getMerchantId()))
                .map(ParcelResponse::from)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
    }

    /** Merchant dashboard: all of this merchant's orders. */
    @Transactional(readOnly = true)
    public List<ParcelResponse> ordersForDashboard(UUID userId) {
        Merchant merchant = byUser(userId);
        return parcels.findByMerchantIdOrderByCreatedAtDesc(merchant.getId()).stream()
                .map(ParcelResponse::from)
                .toList();
    }

    // ---------- helpers ----------

    public Merchant byUser(UUID userId) {
        return merchants.findByUserId(userId)
                .orElseThrow(() -> ApiException.forbidden("Merchant profile not found"));
    }

    private Merchant approvedByUser(UUID userId) {
        Merchant merchant = byUser(userId);
        if (!merchant.isApproved()) {
            throw ApiException.forbidden("Your merchant account is " + merchant.getApprovalStatus()
                    + ". Only approved merchants can do this");
        }
        return merchant;
    }

    private static void validateUrl(String url) {
        try {
            URI uri = URI.create(url.trim());
            if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Webhook URL must be a valid http(s) URL");
        }
    }

    private Role role(RoleName name) {
        return roles.findByName(name).orElseThrow(() -> new IllegalStateException("Role missing: " + name));
    }

    public record NewApiKey(UUID id, String apiKey, String prefix, String note) {
    }

    public record ApiKeyView(UUID id, String prefix, String status, Instant createdAt, Instant revokedAt) {
    }

    public record WebhookSettings(String webhookUrl, String webhookSecret, String note) {
    }
}
