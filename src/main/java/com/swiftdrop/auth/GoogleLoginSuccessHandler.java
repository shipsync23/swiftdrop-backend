package com.swiftdrop.auth;

import com.swiftdrop.common.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Runs after Google has authenticated the user and Spring Security has verified the ID token (report 6.1).
 * We find or create the SwiftDrop user, set our own refresh-token cookie and redirect to the React app.
 * The React app then calls POST /api/v1/auth/refresh to get its access token, so no token appears in the URL.
 */
@Component
public class GoogleLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;
    private final RefreshCookie refreshCookie;
    private final String frontendUrl;

    public GoogleLoginSuccessHandler(AuthService authService, RefreshCookie refreshCookie,
                                     @Value("${swiftdrop.frontend-url}") String frontendUrl) {
        this.authService = authService;
        this.refreshCookie = refreshCookie;
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        // The Google session was only needed for the login redirect; our app uses JWTs from here on.
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        if (!(authentication.getPrincipal() instanceof OidcUser google)) {
            response.sendRedirect(frontendUrl + "/login?error=google");
            return;
        }

        try {
            AuthResult result = authService.loginWithGoogle(
                    google.getSubject(),
                    google.getEmail(),
                    Boolean.TRUE.equals(google.getEmailVerified()),
                    google.getFullName());
            refreshCookie.write(response, result.refreshToken());
            response.sendRedirect(frontendUrl + "/oauth/callback");
        } catch (ApiException ex) {
            response.sendRedirect(frontendUrl + "/login?error=google_not_allowed");
        }
    }
}
