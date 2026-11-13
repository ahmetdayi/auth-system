package tr.gov.kurum.identity.infrastructure.adapter.out.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tr.gov.kurum.identity.application.port.out.AccessTokenRevocationStore;
import tr.gov.kurum.identity.domain.event.AccountSessionsInvalidated;
import tr.gov.kurum.identity.domain.event.RefreshTokenReuseDetected;

/**
 * Turns session invalidations into an access-token cutoff, so already issued access tokens stop working
 * immediately instead of living until they expire.
 *
 * <p><b>Why BEFORE_COMMIT and not AFTER_COMMIT:</b> this is a security side effect, so the failure mode must be
 * "closed". If Redis is down, the exception rolls the transaction back and the caller gets 503: the password
 * is not changed, the ban is not applied — nothing is reported as done while tokens stay valid. The opposite
 * case (Redis written, then the database commit fails) only revokes tokens that would otherwise be valid,
 * which costs the user one silent refresh. This removes the dual-write gap without an outbox table
 * (rule 6.2): no outcome leaves a revoked account with working tokens.</p>
 */
@Component
class AccessTokenRevocationListener {

    private static final Logger log = LoggerFactory.getLogger(AccessTokenRevocationListener.class);

    private final AccessTokenRevocationStore revocationStore;

    AccessTokenRevocationListener(AccessTokenRevocationStore revocationStore) {
        this.revocationStore = revocationStore;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    void on(AccountSessionsInvalidated event) {
        revocationStore.revokeTokensIssuedUntil(event.account(), event.occurredAt());
        log.info("Access tokens revoked: account={}, cause={}", event.account(), event.cause());
    }

    /**
     * The copied chain may have been used to mint access tokens; which ones is unknown, so every token of the
     * account issued so far is revoked. Devices with valid sessions recover with one silent refresh.
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    void on(RefreshTokenReuseDetected event) {
        revocationStore.revokeTokensIssuedUntil(event.account(), event.occurredAt());
        log.warn("Refresh token reuse detected: account={}, session={}", event.account(), event.sessionId());
    }
}
