package tr.gov.kurum.identity.application.port.in;

import java.util.Objects;
import tr.gov.kurum.identity.application.port.out.IssuedAccessToken;
import tr.gov.kurum.identity.domain.model.AccessProfile;
import tr.gov.kurum.identity.domain.model.SessionId;

/**
 * Tokens of a successful login or refresh. The web adapter decides how they travel: browser responses put
 * them only into HttpOnly cookies, mobile responses into the body.
 */
public record AuthenticationResult(
        AccessProfile profile,
        IssuedAccessToken accessToken,
        String refreshToken,
        SessionId sessionId) {

    public AuthenticationResult {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(accessToken, "accessToken");
        Objects.requireNonNull(refreshToken, "refreshToken");
        Objects.requireNonNull(sessionId, "sessionId");
    }

    @Override
    public String toString() {
        return "AuthenticationResult[account=" + profile.accountId().value() + ", session=" + sessionId + ", tokens=PROTECTED]";
    }
}
