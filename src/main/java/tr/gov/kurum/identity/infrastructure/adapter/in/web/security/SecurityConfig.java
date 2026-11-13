package tr.gov.kurum.identity.infrastructure.adapter.in.web.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderNotFoundException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tr.gov.kurum.common.domain.AccountType;
import tr.gov.kurum.common.web.ProblemResponseWriter;
import tr.gov.kurum.identity.application.port.out.AccessTokenRevocationStore;
import tr.gov.kurum.identity.infrastructure.adapter.out.token.AuthorityNames;
import tr.gov.kurum.identity.infrastructure.adapter.out.token.JwtAccessTokenService;

/**
 * HTTP security. Everything is denied to anonymous callers unless listed here as public (fail-closed).
 * Fine-grained rules live next to the endpoints as {@code @PreAuthorize}; the URL rules are the outer wall.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableConfigurationProperties({CookieProperties.class, CorsProperties.class})
public class SecurityConfig {

    /**
     * Scripts and styles only from this origin: the console has no inline script, so an injected
     * {@code <script>} cannot run even if some text were rendered unescaped.
     */
    private static final String CONTENT_SECURITY_POLICY = String.join("; ",
        "default-src 'self'",
        "script-src 'self'",
        "style-src 'self'",
        "img-src 'self' data:",
        "connect-src 'self'",
        "object-src 'none'",
        "base-uri 'none'",
        "form-action 'self'",
        "frame-ancestors 'none'");

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            JwtAccessTokenService tokenService,
                                            AccessTokenRevocationStore revocationStore,
                                            AuthCookies cookies,
                                            CookieProperties cookieProperties,
                                            ProblemResponseWriter problemWriter,
                                            RestAuthenticationEntryPoint entryPoint,
                                            RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        JwtAuthenticationFilter jwtFilter =
            new JwtAuthenticationFilter(tokenService, revocationStore, cookies, problemWriter);

        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfTokenRepository(cookieProperties))
                .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                .ignoringRequestMatchers(
                    "/api/auth/login",      // JSON only: another site cannot send it without a CORS preflight
                    "/api/auth/refresh",    // the attacker can neither read the answer nor keep the new cookie
                    "/api/auth/mobile/**")  // never reads cookies
                // A request with an Authorization header is never authenticated by cookie (see the filter).
                .ignoringRequestMatchers(request -> request.getHeader(HttpHeaders.AUTHORIZATION) != null))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.SAME_ORIGIN)))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/error").permitAll()
                .requestMatchers("/assets/**", "/favicon.ico").permitAll()
                // Console pages are shells; every action they offer calls the API and is checked there.
                .requestMatchers(HttpMethod.GET, "/", "/console", "/console/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                // Must come before the permitAll below: the first matching rule wins.
                .requestMatchers(HttpMethod.POST, "/api/auth/logout-all").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/auth/**").permitAll()
                // TYPE_ADMIN, not ROLE_ADMIN: a role is data and could be assigned to a customer by mistake,
                // the type authority is produced in code from the account's type.
                .requestMatchers("/api/admin/**").hasAuthority(AuthorityNames.typeAuthority(AccountType.ADMIN))
                .anyRequest().authenticated())
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(accessDeniedHandler))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private static CookieCsrfTokenRepository csrfTokenRepository(CookieProperties cookieProperties) {
        // Readable by scripts on purpose: the page copies it into X-XSRF-TOKEN. Knowing it grants nothing,
        // the access token stays HttpOnly.
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> {
            cookie.secure(cookieProperties.secure()).sameSite("Strict").path("/");
            if (cookieProperties.csrfDomain() != null && !cookieProperties.csrfDomain().isBlank()) {
                cookie.domain(cookieProperties.csrfDomain());
            }
        });
        return repository;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsProperties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, "X-XSRF-TOKEN"));
        config.setAllowCredentials(true);
        config.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /**
     * Credentials are checked by the authentication use case, not by Spring's AuthenticationManager. This bean
     * also stops Spring Boot from creating its default in-memory user with a generated password.
     */
    @Bean
    AuthenticationManager authenticationManager() {
        return authentication -> {
            throw new ProviderNotFoundException("Authentication happens through /api/auth/login");
        };
    }
}
