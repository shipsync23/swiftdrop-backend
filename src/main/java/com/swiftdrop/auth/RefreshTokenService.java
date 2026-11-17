package com.swiftdrop.auth;

import com.swiftdrop.common.exception.ApiException;
import com.swiftdrop.common.security.TokenHasher;
import com.swiftdrop.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Refresh tokens: random opaque value, 7-day expiry, stored hashed,
 * rotated on every use and revoked on logout (report 6.2).
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final Duration ttl;

    public RefreshTokenService(RefreshTokenRepository repository,
                               @Value("${swiftdrop.jwt.refresh-token-days}") long refreshTokenDays) {
        this.repository = repository;
        this.ttl = Duration.ofDays(refreshTokenDays);
    }

    /** Creates a new refresh token and returns the RAW value (only the hash is saved). */
    @Transactional
    public String create(User user) {
        String raw = TokenHasher.randomToken();
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(TokenHasher.sha256(raw));
        token.setExpiresAt(Instant.now().plus(ttl));
        repository.save(token);
        return raw;
    }

    /**
     * Validates a refresh token, revokes it and returns its user (rotation).
     * If an already-revoked token is used again, it may have been stolen, so every
     * session of that user is revoked. (noRollbackFor keeps that revocation even though we throw.)
     */
    @Transactional(noRollbackFor = ApiException.class)
    public User consume(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw ApiException.unauthorized("Session expired. Please log in again");
        }
        Instant now = Instant.now();
        RefreshToken token = repository.findByTokenHash(TokenHasher.sha256(rawToken))
                .orElseThrow(() -> ApiException.unauthorized("Session expired. Please log in again"));

        if (token.getRevokedAt() != null) {
            repository.revokeAllForUser(token.getUser().getId(), now);
            throw ApiException.unauthorized("Session expired. Please log in again");
        }
        if (!token.isUsable(now)) {
            throw ApiException.unauthorized("Session expired. Please log in again");
        }
        token.setRevokedAt(now);
        return token.getUser();
    }

    /** Logout: revoke the given token if it exists. Unknown tokens are ignored. */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.findByTokenHash(TokenHasher.sha256(rawToken))
                .filter(t -> t.getRevokedAt() == null)
                .ifPresent(t -> t.setRevokedAt(Instant.now()));
    }
}
