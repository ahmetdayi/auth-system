package tr.gov.kurum.identity.infrastructure.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.BusinessRuleException;
import tr.gov.kurum.common.domain.ErrorKey;
import tr.gov.kurum.common.web.AuthenticatedAccount;
import tr.gov.kurum.common.web.CurrentAccount;
import tr.gov.kurum.common.web.ProblemFactory;
import tr.gov.kurum.identity.application.port.in.AuthenticateCommand;
import tr.gov.kurum.identity.application.port.in.AuthenticationResult;
import tr.gov.kurum.identity.application.port.in.AuthenticationUseCase;
import tr.gov.kurum.identity.application.port.in.CustomerRegistrationUseCase;
import tr.gov.kurum.identity.application.port.in.LogoutCommand;
import tr.gov.kurum.identity.application.port.in.RefreshSessionCommand;
import tr.gov.kurum.identity.application.port.in.RegisterCustomerCommand;
import tr.gov.kurum.identity.application.port.in.RevocableAccessToken;
import tr.gov.kurum.identity.domain.model.IdentityErrorKey;
import tr.gov.kurum.identity.infrastructure.adapter.in.web.AuthDtos.BrowserSessionResponse;
import tr.gov.kurum.identity.infrastructure.adapter.in.web.AuthDtos.CreatedResponse;
import tr.gov.kurum.identity.infrastructure.adapter.in.web.AuthDtos.LoginRequest;
import tr.gov.kurum.identity.infrastructure.adapter.in.web.AuthDtos.RegisterRequest;
import tr.gov.kurum.identity.infrastructure.adapter.in.web.security.AuthCookies;
import tr.gov.kurum.identity.infrastructure.adapter.in.web.security.SecurityErrorKey;
import tr.gov.kurum.identity.infrastructure.adapter.out.token.JwtAccessTokenService;

/**
 * Browser authentication. Tokens are written only to HttpOnly cookies and never appear in a response body:
 * if they did, a script injected into the page could read them and the HttpOnly flag would protect nothing.
 */
@RestController
@RequestMapping("/api/auth")
class AuthController {

    /** Refresh failures after which the browser's cookies are useless and are removed. */
    private static final Set<ErrorKey> TERMINAL_REFRESH_FAILURES =
        Set.of(IdentityErrorKey.REFRESH_TOKEN_INVALID, IdentityErrorKey.SESSION_ENDED);

    private final CustomerRegistrationUseCase registration;
    private final AuthenticationUseCase authentication;
    private final AuthCookies cookies;
    private final JwtAccessTokenService tokenService;
    private final ProblemFactory problemFactory;

    AuthController(CustomerRegistrationUseCase registration, AuthenticationUseCase authentication,
                   AuthCookies cookies, JwtAccessTokenService tokenService, ProblemFactory problemFactory) {
        this.registration = registration;
        this.authentication = authentication;
        this.cookies = cookies;
        this.tokenService = tokenService;
        this.problemFactory = problemFactory;
    }

    /**
     * CSRF-protected (not in the ignore list), like every cookie-carrying state change. Opens the account; the
     * customer profile follows a moment later, when the outbox delivers {@code CustomerAccountOpened}.
     */
    @PostMapping("/register")
    ResponseEntity<CreatedResponse> register(@RequestBody @Valid RegisterRequest body) {
        AccountId id = registration.register(
            new RegisterCustomerCommand(body.email(), body.password(), body.firstName(), body.lastName()));
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreatedResponse(id.value()));
    }

    @PostMapping("/login")
    BrowserSessionResponse login(@RequestBody @Valid LoginRequest body, HttpServletRequest request,
                                 HttpServletResponse response) {
        AuthenticationResult result = authentication.authenticate(new AuthenticateCommand(
            body.email(), body.password(), body.userType(), ClientDetailsResolver.from(request)));
        cookies.write(response, result);
        return BrowserSessionResponse.from(result);
    }

    @PostMapping("/refresh")
    ResponseEntity<?> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = cookies.readRefreshToken(request);
        if (refreshToken == null) {
            throw new BusinessRuleException(SecurityErrorKey.REFRESH_TOKEN_MISSING);
        }
        AuthenticationResult result;
        try {
            result = authentication.refresh(
                new RefreshSessionCommand(refreshToken, ClientDetailsResolver.from(request)));
        } catch (BusinessRuleException e) {
            if (!TERMINAL_REFRESH_FAILURES.contains(e.errorKey())) {
                // e.g. REFRESH_IN_PROGRESS: another tab has just rotated the cookies; leave them alone.
                throw e;
            }
            cookies.clear(response);
            ProblemDetail problem = problemFactory.create(e.errorKey(), e.arguments(), request.getRequestURI());
            return ResponseEntity.status(problem.getStatus()).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
        }
        cookies.write(response, result);
        return ResponseEntity.ok(BrowserSessionResponse.from(result));
    }

    /** CSRF-protected: another site must not be able to log the user out. Works with an expired token too. */
    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        RevocableAccessToken accessToken = tokenService.verifyLeniently(cookies.readAccessToken(request))
            .map(token -> new RevocableAccessToken(token.tokenId(), token.expiresAt()))
            .orElse(null);
        authentication.logout(new LogoutCommand(cookies.readRefreshToken(request), accessToken));
        cookies.clear(response);
        return ResponseEntity.noContent().build();
    }

    /** Requires a valid access token (see SecurityConfig). Used by browser and mobile clients alike. */
    @PostMapping("/logout-all")
    ResponseEntity<Void> logoutAll(@CurrentAccount AuthenticatedAccount account, HttpServletResponse response) {
        authentication.logoutEverywhere(account.accountId());
        cookies.clear(response);
        return ResponseEntity.noContent().build();
    }
}
