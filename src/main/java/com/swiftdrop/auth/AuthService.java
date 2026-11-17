package com.swiftdrop.auth;

import com.swiftdrop.auth.dto.AuthResponse;
import com.swiftdrop.auth.dto.LoginRequest;
import com.swiftdrop.auth.dto.RegisterRequest;
import com.swiftdrop.auth.dto.UserResponse;
import com.swiftdrop.common.exception.ApiException;
import com.swiftdrop.user.Role;
import com.swiftdrop.user.RoleName;
import com.swiftdrop.user.RoleRepository;
import com.swiftdrop.user.User;
import com.swiftdrop.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

/** Business logic for registration, login (email and Google), token refresh and logout. */
@Service
public class AuthService {

    private static final String INVALID_LOGIN = "Invalid email or password";

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;

    public AuthService(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
                       JwtService jwtService, RefreshTokenService refreshTokens) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
    }

    /** Customer self-registration. Staff accounts are created by an admin. */
    @Transactional
    public AuthResult registerCustomer(RegisterRequest request) {
        String email = normalise(request.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setPhone(request.phone());
        user.setRole(role(RoleName.CUSTOMER));
        users.save(user);
        return issueTokens(user);
    }

    @Transactional
    public AuthResult login(LoginRequest request) {
        User user = users.findByEmailIgnoreCase(normalise(request.email()))
                .orElseThrow(() -> ApiException.unauthorized(INVALID_LOGIN));

        // Same message for "no such user" and "wrong password" so attackers cannot discover emails.
        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized(INVALID_LOGIN);
        }
        ensureActive(user);
        return issueTokens(user);
    }

    /**
     * Google login (report 6.1): match by google id; otherwise link to an existing account only if
     * Google verified the email AND that account is a CUSTOMER, so staff accounts cannot be taken over.
     * Otherwise create a new CUSTOMER.
     */
    @Transactional
    public AuthResult loginWithGoogle(String googleId, String email, boolean emailVerified, String fullName) {
        User user = users.findByGoogleId(googleId).orElse(null);

        if (user == null && email != null) {
            User existing = users.findByEmailIgnoreCase(normalise(email)).orElse(null);
            if (existing != null) {
                if (!emailVerified || existing.roleName() != RoleName.CUSTOMER) {
                    throw ApiException.forbidden("This account cannot sign in with Google");
                }
                existing.setGoogleId(googleId);
                user = existing;
            }
        }

        if (user == null) {
            if (email == null || !emailVerified) {
                throw ApiException.forbidden("Google did not provide a verified email");
            }
            user = new User();
            user.setEmail(normalise(email));
            user.setGoogleId(googleId);
            user.setFullName(fullName != null && !fullName.isBlank() ? fullName : email);
            user.setRole(role(RoleName.CUSTOMER));
            users.save(user);
        }

        ensureActive(user);
        return issueTokens(user);
    }

    /**
     * Exchange a refresh token (from the cookie) for a new access token and a new refresh token.
     * noRollbackFor: if a stolen (already used) token is detected, the "revoke all sessions"
     * change must be saved even though we return an error.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult refresh(String rawRefreshToken) {
        User user = refreshTokens.consume(rawRefreshToken);
        ensureActive(user);
        return issueTokens(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokens.revoke(rawRefreshToken);
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(UUID userId) {
        return users.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> ApiException.notFound("User not found"));
    }

    // ---------- helpers ----------

    private AuthResult issueTokens(User user) {
        String accessToken = jwtService.issueAccessToken(user);
        String refreshToken = refreshTokens.create(user);
        AuthResponse body = new AuthResponse(accessToken, "Bearer",
                jwtService.accessTokenTtlSeconds(), UserResponse.from(user));
        return new AuthResult(body, refreshToken);
    }

    private void ensureActive(User user) {
        if (!user.isActive()) {
            throw ApiException.forbidden("This account is disabled");
        }
    }

    private Role role(RoleName name) {
        return roles.findByName(name)
                .orElseThrow(() -> new IllegalStateException("Role missing from database: " + name));
    }

    private static String normalise(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
