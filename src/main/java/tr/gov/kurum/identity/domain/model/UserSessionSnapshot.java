package tr.gov.kurum.identity.domain.model;

import java.time.Instant;
import tr.gov.kurum.common.domain.AccountId;

/** Full state of a {@link UserSession} as read from storage. */
public record UserSessionSnapshot(
        SessionId id,
        long version,
        AccountId owner,
        ClientDetails client,
        Instant startedAt,
        Instant lastRefreshedAt,
        Instant expiresAt,
        RefreshTokenHash currentTokenHash,
        Instant endedAt,
        SessionEndReason endReason) {
}
