package tr.gov.kurum.identity.infrastructure.adapter.in.web.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tr.gov.kurum.common.web.AuthenticatedAccount;
import tr.gov.kurum.common.web.ProblemResponseWriter;
import tr.gov.kurum.identity.application.port.out.AccessTokenRevocationStore;
import tr.gov.kurum.identity.infrastructure.adapter.out.token.JwtAccessTokenService;
import tr.gov.kurum.identity.infrastructure.adapter.out.token.VerifiedAccessToken;

/**
 * Authenticates a request from its access token, without touching the database.
 *
 * <ul>
 *   <li>No token: continue anonymously.</li>
 *   <li>Invalid, expired or revoked token: continue anonymously and record the reason in a request
 *       attribute. Public endpoints (login, refresh) keep working with a stale cookie; protected ones are
 *       answered 401 by the entry point, which reports the reason.</li>
 *   <li>Revocation store unreachable: API requests get 503 (fail-closed). Console HTML navigations load
 *       anonymously instead of a raw JSON problem body in the browser tab.</li>
 *   <li>Browser cookies: a valid {@code support_access_token} is preferred over {@code access_token} for
 *       paths outside {@code /api/support/**} (and for {@code GET /api/support/session}), so the agent acts
 *       as the customer for profile APIs while still using their own token for grant management.</li>
 * </ul>
 *
 * <p>Not a Spring bean: Spring Boot registers every {@code Filter} bean with the servlet container as well,
 * so a bean would run twice.</p>
 */
public final class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String TOKEN_FAILURE_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".TOKEN_FAILURE";

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtAccessTokenService tokenService;
    private final AccessTokenRevocationStore revocationStore;
    private final AuthCookies cookies;
    private final ProblemResponseWriter problemWriter;

    public JwtAuthenticationFilter(JwtAccessTokenService tokenService, AccessTokenRevocationStore revocationStore,
                                   AuthCookies cookies, ProblemResponseWriter problemWriter) {
        this.tokenService = tokenService;
        this.revocationStore = revocationStore;
        this.cookies = cookies;
        this.problemWriter = problemWriter;
    }

    /** Static files never need the caller's identity; skipping them saves a Redis round trip per asset. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/assets/") || "/favicon.ico".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && !header.startsWith(BEARER_PREFIX)) {
            continueAnonymously(request, response, chain, SecurityErrorKey.TOKEN_INVALID);
            return;
        }
        // With an Authorization header only the header counts, never the cookie: the CSRF exemption for
        // requests carrying this header relies on exactly that.
        String token = header != null ? header.substring(BEARER_PREFIX.length()).trim()
            : resolveCookieToken(request);
        if (token == null) {
            chain.doFilter(request, response);
            return;
        }

        VerifiedAccessToken verified;
        try {
            verified = tokenService.verify(token);
        } catch (ExpiredJwtException e) {
            continueAnonymously(request, response, chain, SecurityErrorKey.TOKEN_EXPIRED);
            return;
        } catch (JwtException | IllegalArgumentException e) {
            continueAnonymously(request, response, chain, SecurityErrorKey.TOKEN_INVALID);
            return;
        }

        // Only after the signature is verified: garbage tokens never cost a Redis call.
        boolean revoked;
        try {
            revoked = revocationStore.isRevoked(verified.tokenId(), verified.account(), verified.issuedAt());
        } catch (DataAccessException | IllegalStateException e) {
            log.error("Revocation store unavailable, request rejected: path={}, cause={}",
                request.getRequestURI(), e.getMessage());
            // API stays fail-closed (503 JSON). Console HTML navigations must not become a black JSON page —
            // load the Thymeleaf view anonymously; /api/* calls will still surface AUTH_UNAVAILABLE.
            if (isConsoleHtmlNavigation(request)) {
                continueAnonymously(request, response, chain, SecurityErrorKey.AUTH_UNAVAILABLE);
                return;
            }
            problemWriter.write(request, response, SecurityErrorKey.AUTH_UNAVAILABLE);
            return;
        }
        if (revoked) {
            continueAnonymously(request, response, chain, SecurityErrorKey.TOKEN_REVOKED);
            return;
        }

        AuthenticatedAccount account = verified.isSupportSession()
            ? AuthenticatedAccount.supportSession(verified.account(), verified.sessionId().value(),
                verified.email(), verified.tokenId(), verified.expiresAt(), verified.actorId(), verified.grantId())
            : AuthenticatedAccount.of(verified.account(), verified.type(), verified.sessionId().value(),
                verified.email(), verified.tokenId(), verified.expiresAt());
        List<SimpleGrantedAuthority> authorities = verified.authorities().stream()
            .map(SimpleGrantedAuthority::new)
            .toList();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(account, null, authorities));
        SecurityContextHolder.setContext(context);

        chain.doFilter(request, response);
    }

    /**
     * Prefer a valid support cookie for customer-facing paths; keep the agent's own access cookie for
     * {@code /api/support/**} grant APIs (except the session probe).
     */
    private String resolveCookieToken(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean preferSupport = !path.startsWith("/api/support/") || "/api/support/session".equals(path);
        if (preferSupport) {
            String support = cookies.readSupportAccessToken(request);
            if (support != null) {
                try {
                    tokenService.verify(support);
                    return support;
                } catch (JwtException | IllegalArgumentException ignored) {
                    // Fall through to the agent's normal access cookie.
                }
            }
        }
        return cookies.readAccessToken(request);
    }

    private static void continueAnonymously(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain chain, SecurityErrorKey failure)
            throws ServletException, IOException {
        request.setAttribute(TOKEN_FAILURE_ATTRIBUTE, failure);
        chain.doFilter(request, response);
    }

    /** Browser document GET under /console — not XHR/fetch API calls. */
    private static boolean isConsoleHtmlNavigation(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!path.equals("/console") && !path.startsWith("/console/")) {
            return false;
        }
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        return accept == null || accept.contains("text/html");
    }
}
