package tr.gov.kurum.identity.infrastructure.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Clock;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tr.gov.kurum.identity.application.port.in.AuthenticateCommand;
import tr.gov.kurum.identity.application.port.in.AuthenticationUseCase;
import tr.gov.kurum.identity.application.port.in.LogoutCommand;
import tr.gov.kurum.identity.application.port.in.RefreshSessionCommand;
import tr.gov.kurum.identity.application.port.in.RevocableAccessToken;
import tr.gov.kurum.identity.infrastructure.adapter.in.web.AuthDtos.LoginRequest;
import tr.gov.kurum.identity.infrastructure.adapter.in.web.AuthDtos.MobileTokenResponse;
import tr.gov.kurum.identity.infrastructure.adapter.in.web.AuthDtos.RefreshTokenRequest;
import tr.gov.kurum.identity.infrastructure.adapter.out.token.JwtAccessTokenService;

/**
 * Mobile and server clients: tokens in the body, access token sent back as a Bearer header.
 *
 * <p>Never reads a cookie. A script injected into the browser console therefore cannot use these endpoints
 * to get tokens out of the HttpOnly cookies: it would need the raw refresh token, which it cannot read.</p>
 */
@RestController
@RequestMapping("/api/auth/mobile")
class MobileAuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthenticationUseCase authentication;
    private final JwtAccessTokenService tokenService;
    private final Clock clock;

    MobileAuthController(AuthenticationUseCase authentication, JwtAccessTokenService tokenService, Clock clock) {
        this.authentication = authentication;
        this.tokenService = tokenService;
        this.clock = clock;
    }

    @PostMapping("/login")
    MobileTokenResponse login(@RequestBody @Valid LoginRequest body, HttpServletRequest request) {
        return MobileTokenResponse.from(authentication.authenticate(new AuthenticateCommand(
            body.email(), body.password(), body.userType(), ClientDetailsResolver.from(request))), clock);
    }

    @PostMapping("/refresh")
    MobileTokenResponse refresh(@RequestBody @Valid RefreshTokenRequest body, HttpServletRequest request) {
        return MobileTokenResponse.from(authentication.refresh(
            new RefreshSessionCommand(body.refreshToken(), ClientDetailsResolver.from(request))), clock);
    }

    /** Ends the session of the refresh token and, if a Bearer token is sent, revokes that access token too. */
    @PostMapping("/logout")
    ResponseEntity<Void> logout(@RequestBody @Valid RefreshTokenRequest body,
                                @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
                                String authorization) {
        String rawAccessToken = authorization != null && authorization.startsWith(BEARER_PREFIX)
            ? authorization.substring(BEARER_PREFIX.length()).trim()
            : null;
        RevocableAccessToken accessToken = tokenService.verifyLeniently(rawAccessToken)
            .map(token -> new RevocableAccessToken(token.tokenId(), token.expiresAt()))
            .orElse(null);
        authentication.logout(new LogoutCommand(body.refreshToken(), accessToken));
        return ResponseEntity.noContent().build();
    }
}
