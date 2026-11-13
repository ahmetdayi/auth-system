package tr.gov.kurum.identity.infrastructure.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import tr.gov.kurum.common.domain.AccountType;
import tr.gov.kurum.common.web.AuthenticatedAccount;
import tr.gov.kurum.identity.application.port.in.AuthenticationResult;
import tr.gov.kurum.identity.infrastructure.adapter.out.token.AuthorityNames;

/**
 * Request and response bodies of the authentication endpoints.
 *
 * <p>Size limits here only stop oversized input early. The rules themselves (e-mail format, password length
 * in UTF-8 bytes) are enforced by the domain value objects, so they hold for every entry point.</p>
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** The JSON field stays {@code userType}: it is part of the API contract. */
    public record LoginRequest(
            @NotBlank @Size(max = 254) String email,
            @NotBlank @Size(max = 200) String password,
            @NotNull AccountType userType) {

        @Override
        public String toString() {
            return "LoginRequest[userType=" + userType + ", password=PROTECTED]";
        }
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 254) String email,
            @NotBlank @Size(max = 200) String password,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName) {

        @Override
        public String toString() {
            return "RegisterRequest[password=PROTECTED]";
        }
    }

    public record RefreshTokenRequest(@NotBlank @Size(max = 200) String refreshToken) {

        @Override
        public String toString() {
            return "RefreshTokenRequest[refreshToken=PROTECTED]";
        }
    }

    public record CreatedResponse(long id) {
    }

    /** Browser login/refresh answer. No token in the body: they travel only in HttpOnly cookies. */
    public record BrowserSessionResponse(
            AccountType accountType,
            long accountId,
            String email,
            long sessionId,
            Instant accessTokenExpiresAt,
            List<String> authorities) {

        public BrowserSessionResponse {
            authorities = List.copyOf(authorities);
        }

        static BrowserSessionResponse from(AuthenticationResult result) {
            return new BrowserSessionResponse(
                result.profile().type(),
                result.profile().accountId().value(),
                result.profile().email().value(),
                result.sessionId().value(),
                result.accessToken().expiresAt(),
                AuthorityNames.of(result.profile()));
        }
    }

    /** Mobile / server client answer: the client stores the tokens itself and sends a Bearer header. */
    public record MobileTokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            long sessionId) {

        static MobileTokenResponse from(AuthenticationResult result, Clock clock) {
            long expiresIn = Math.max(0, Duration.between(clock.instant(), result.accessToken().expiresAt())
                .toSeconds());
            return new MobileTokenResponse(result.accessToken().token(), result.refreshToken(), "Bearer",
                expiresIn, result.sessionId().value());
        }

        @Override
        public String toString() {
            return "MobileTokenResponse[tokens=PROTECTED, sessionId=" + sessionId + "]";
        }
    }

    /** What the server knows about the caller from the access token alone. */
    public record MeResponse(
            AccountType accountType,
            long accountId,
            String email,
            long sessionId,
            Instant accessTokenExpiresAt,
            List<String> authorities) {

        public MeResponse {
            authorities = List.copyOf(authorities);
        }

        static MeResponse of(AuthenticatedAccount account, List<String> authorities) {
            return new MeResponse(account.type(), account.accountId().value(), account.email(),
                account.sessionId(), account.tokenExpiresAt(), authorities);
        }
    }
}
