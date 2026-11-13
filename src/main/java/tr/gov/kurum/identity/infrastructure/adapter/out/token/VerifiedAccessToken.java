package tr.gov.kurum.identity.infrastructure.adapter.out.token;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.AccountType;
import tr.gov.kurum.identity.domain.model.SessionId;

/** Claims of an access token whose signature, issuer, type and expiry have been verified. */
public record VerifiedAccessToken(
        String tokenId,
        AccountId account,
        AccountType type,
        SessionId sessionId,
        String email,
        List<String> authorities,
        Instant issuedAt,
        Instant expiresAt) {

    public VerifiedAccessToken {
        Objects.requireNonNull(tokenId, "tokenId");
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(email, "email");
        authorities = List.copyOf(authorities);
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
