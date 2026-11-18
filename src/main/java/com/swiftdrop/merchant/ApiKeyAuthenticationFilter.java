package com.swiftdrop.merchant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.security.TokenHasher;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Authenticates merchant platforms on /api/v1/orders/** with the "X-API-Key" header (report 5.2.4).
 *  - missing / unknown / revoked key -> 401
 *  - merchant not approved or disabled -> 403
 *  - valid -> authority ROLE_MERCHANT_API, principal = MerchantPrincipal
 * Not a Spring bean on purpose, so it runs only inside the security filter chain.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";
    private static final String PATH = "/api/v1/orders";

    private final ApiCredentialRepository credentials;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthenticationFilter(ApiCredentialRepository credentials, ObjectMapper objectMapper) {
        this.credentials = credentials;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !(path.equals(PATH) || path.startsWith(PATH + "/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String rawKey = request.getHeader(HEADER);
        if (rawKey == null || rawKey.isBlank()) {
            reject(response, HttpStatus.UNAUTHORIZED, "Missing X-API-Key header");
            return;
        }
        ApiCredential credential = credentials.findByKeyHash(TokenHasher.sha256(rawKey.trim())).orElse(null);
        if (credential == null || !"ACTIVE".equals(credential.getStatus())) {
            reject(response, HttpStatus.UNAUTHORIZED, "Invalid API key");
            return;
        }
        Merchant merchant = credential.getMerchant();
        if (!merchant.isApproved() || !merchant.getUser().isActive()) {
            reject(response, HttpStatus.FORBIDDEN, "Merchant account is not approved");
            return;
        }

        var auth = new UsernamePasswordAuthenticationToken(
                new MerchantPrincipal(merchant.getId(), merchant.getBusinessName()), null,
                List.of(new SimpleGrantedAuthority("ROLE_MERCHANT_API")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void reject(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("correlationId", UUID.randomUUID().toString());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
