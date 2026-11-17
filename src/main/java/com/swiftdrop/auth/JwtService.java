package com.swiftdrop.auth;

import com.swiftdrop.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Creates SwiftDrop access tokens.
 * Claims: sub (user id), role, iat, exp. No personal data goes into the token (report 6.2).
 */
@Service
public class JwtService {

    public static final String ROLE_CLAIM = "role";

    private final JwtEncoder encoder;
    private final Duration accessTokenTtl;

    public JwtService(JwtEncoder encoder,
                      @Value("${swiftdrop.jwt.access-token-minutes}") long accessTokenMinutes) {
        this.encoder = encoder;
        this.accessTokenTtl = Duration.ofMinutes(accessTokenMinutes);
    }

    public String issueAccessToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("swiftdrop")
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(accessTokenTtl))
                .claim(ROLE_CLAIM, user.roleName().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long accessTokenTtlSeconds() {
        return accessTokenTtl.toSeconds();
    }
}
