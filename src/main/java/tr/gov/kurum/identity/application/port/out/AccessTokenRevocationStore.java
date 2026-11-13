package tr.gov.kurum.identity.application.port.out;

import java.time.Instant;
import tr.gov.kurum.common.domain.AccountId;

/**
 * Revocation of access tokens before they expire. Shared by every application instance.
 * Implementations must fail loudly (throw) when the store is unreachable; callers decide to fail closed.
 */
public interface AccessTokenRevocationStore {

    /** Logout: revoke one token until it would have expired anyway. */
    void revokeToken(String tokenId, Instant expiresAt);

    /**
     * Ban, password change, logout everywhere: revoke every token of the account issued at or before the
     * cutoff (a token of the same millisecond counts as before: fail-closed).
     */
    void revokeTokensIssuedUntil(AccountId account, Instant cutoff);

    boolean isRevoked(String tokenId, AccountId account, Instant issuedAt);
}
