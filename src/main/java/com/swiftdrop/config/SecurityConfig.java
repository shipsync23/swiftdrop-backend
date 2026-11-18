package com.swiftdrop.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.auth.GoogleLoginSuccessHandler;
import com.swiftdrop.auth.JwtService;
import com.swiftdrop.merchant.ApiCredentialRepository;
import com.swiftdrop.merchant.ApiKeyAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Security rules (report section 6):
 *  - Every request carries "Authorization: Bearer <JWT>", validated by the resource server.
 *  - The JWT "role" claim becomes ROLE_CUSTOMER, ROLE_COURIER ... for @PreAuthorize checks.
 *  - Google login uses the Authorization Code flow with state + PKCE.
 *  - Unauthenticated -> 401 JSON, wrong role -> 403 JSON (never an HTML login page).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** Endpoints anyone can call without logging in. */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/ping",
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/merchants/register",  // merchant self-registration (starts PENDING)
            "/mock-merchant/**",            // demo webhook receiver
            "/api/v1/track/**",          // public tracking page
            "/api/v1/zones",              // zone list for the booking form
            "/actuator/health",
            "/actuator/health/**",
            "/oauth2/**",                 // starts Google login
            "/login/oauth2/**",           // Google redirects back here
            "/error"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   ClientRegistrationRepository clientRegistrations,
                                                   GoogleLoginSuccessHandler googleSuccessHandler,
                                                   ObjectMapper objectMapper,
                                                   ApiCredentialRepository apiCredentials,
                                                   @Value("${swiftdrop.frontend-url}") String frontendUrl)
            throws Exception {
        http
                // The API uses Bearer tokens, not session cookies, so CSRF protection is not needed.
                // The refresh cookie is SameSite and only accepted by /api/v1/auth/*.
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                // A short session exists only during the Google redirect; the API itself is stateless.
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers("/api/v1/orders", "/api/v1/orders/**").hasRole("MERCHANT_API")
                        .anyRequest().authenticated())
                .oauth2Login(oauth -> oauth
                        .authorizationEndpoint(ep -> ep.authorizationRequestResolver(pkceResolver(clientRegistrations)))
                        .successHandler(googleSuccessHandler)
                        .failureHandler((req, res, ex) -> res.sendRedirect(frontendUrl + "/login?error=google")))
                .oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                // Merchant platforms authenticate with X-API-Key on /api/v1/orders (report 5.2.4)
                .addFilterBefore(new ApiKeyAuthenticationFilter(apiCredentials, objectMapper),
                        BearerTokenAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) ->
                                writeProblem(res, objectMapper, HttpStatus.UNAUTHORIZED, "Authentication required"))
                        .accessDeniedHandler((req, res, e) ->
                                writeProblem(res, objectMapper, HttpStatus.FORBIDDEN, "You do not have permission to do this")));

        return http.build();
    }

    /** Turns the "role" claim (e.g. "COURIER") into the authority "ROLE_COURIER". */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(JwtService.ROLE_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /** bcrypt with cost factor 12 (report 6.4). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /** CORS: only the React frontend may call the API from a browser. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${swiftdrop.frontend-url}") String frontendUrl) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(frontendUrl));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-API-Key"));
        config.setAllowCredentials(true);   // needed for the refresh cookie
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** Adds PKCE (code_challenge) to the Google authorization request (report 6.1). */
    private OAuth2AuthorizationRequestResolver pkceResolver(ClientRegistrationRepository repo) {
        DefaultOAuth2AuthorizationRequestResolver resolver =
                new DefaultOAuth2AuthorizationRequestResolver(repo, "/oauth2/authorization");
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        return resolver;
    }

    /** Same JSON shape as GlobalExceptionHandler, for errors raised inside the security filters. */
    private static void writeProblem(HttpServletResponse response, ObjectMapper mapper,
                                     HttpStatus status, String detail) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("correlationId", UUID.randomUUID().toString());

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), body);
    }
}
