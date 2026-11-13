package tr.gov.kurum.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.identity.application.port.in.PurgeExpiredSessionsUseCase;
import tr.gov.kurum.identity.application.port.out.UserSessionRepository;
import tr.gov.kurum.identity.domain.model.SessionId;
import tr.gov.kurum.identity.domain.model.UserSession;
import tr.gov.kurum.support.ApiClient.Response;
import tr.gov.kurum.support.IntegrationTestBase;
import tr.gov.kurum.support.TestData.Account;

/** Behaviour that only a real PostgreSQL shows: versions, constraints, cascades, batched deletes. */
class SessionPersistenceIntegrationTest extends IntegrationTestBase {

    @Autowired
    private UserSessionRepository sessions;

    @Autowired
    private PurgeExpiredSessionsUseCase purge;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void a_stale_aggregate_cannot_overwrite_a_newer_one() {
        Account customer = data.customer();
        long sessionId = mobileOf(customer).get("/api/me").body().get("sessionId").asLong();
        AccountId owner = new AccountId(customer.id());

        UserSession first = load(sessionId);
        UserSession second = load(sessionId);
        first.revokeByAdmin(owner, clock.instant());
        transactionTemplate.executeWithoutResult(status -> sessions.save(first));
        second.revokeByOwner(owner, clock.instant());

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> sessions.save(second)))
            .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(load(sessionId).endReason()).hasValueSatisfying(
            reason -> assertThat(reason.name()).isEqualTo("REVOKED_BY_ADMIN"));
    }

    @Test
    void only_hashes_of_refresh_tokens_are_stored() {
        Account customer = data.customer();
        Response login = client().mobileLogin(customer.email(), tr.gov.kurum.support.TestData.PASSWORD, "CUSTOMER");
        String rawToken = login.text("refreshToken");
        client().post("/api/auth/mobile/refresh", Map.of("refreshToken", rawToken));

        long rawStored = data.queryLong("""
            SELECT count(*) FROM user_sessions s LEFT JOIN retired_refresh_tokens r ON r.session_id = s.id
            WHERE s.current_token_hash = ? OR r.token_hash = ?
            """, rawToken, rawToken);
        long retired = data.queryLong("SELECT count(*) FROM retired_refresh_tokens WHERE session_id = ?",
            login.body().get("sessionId").asLong());

        assertThat(rawStored).isZero();
        assertThat(retired).isEqualTo(1);
    }

    @Test
    void expired_sessions_are_purged_in_batches_with_their_token_history() {
        Account customer = data.customer();
        long oldSession = rotatedSessionOf(customer);
        long otherOldSession = rotatedSessionOf(customer);

        clock.advance(Duration.ofDays(15));
        long freshSession = mobileOf(customer).get("/api/me").body().get("sessionId").asLong();

        int total = 0;
        int deleted;
        do {
            deleted = purge.purgeExpiredSessions(1);   // batch of one: proves the loop works in chunks
            total += deleted;
        } while (deleted == 1);

        assertThat(total).isGreaterThanOrEqualTo(2);
        assertThat(data.queryLong("SELECT count(*) FROM user_sessions WHERE id IN (?, ?)", oldSession,
            otherOldSession)).isZero();
        assertThat(data.queryLong("SELECT count(*) FROM retired_refresh_tokens WHERE session_id IN (?, ?)",
            oldSession, otherOldSession)).isZero();
        assertThat(data.queryLong("SELECT count(*) FROM user_sessions WHERE id = ?", freshSession)).isOne();
        assertThat(purge.purgeExpiredSessions(100)).isZero();   // idempotent: a second run finds nothing
    }

    @Test
    void database_constraints_hold_even_when_code_is_bypassed() {
        Account customer = data.customer();

        assertThatThrownBy(() -> jdbc.update("UPDATE accounts SET email = upper(email) WHERE id = ?", customer.id()))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE accounts SET status = 'BANNED' WHERE id = ?", customer.id()))
            .isInstanceOf(DataIntegrityViolationException.class);
        // Customers have no privilege level; only admins do.
        assertThatThrownBy(() -> jdbc.update("UPDATE accounts SET admin_level = 'SUPER' WHERE id = ?", customer.id()))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO memberships (account_id, tier, starts_at, ends_at, created_at, updated_at, version)
                VALUES (?, 'GOLD', now(), now() - interval '1 day', now(), now(), 0)
                """, customer.id())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO roles (id, name, created_at, updated_at, version)
                VALUES (nextval('role_id_seq'), 'ROLE_ADMIN', now(), now(), 0)
                """)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private long rotatedSessionOf(Account customer) {
        Response login = client().mobileLogin(customer.email(), tr.gov.kurum.support.TestData.PASSWORD, "CUSTOMER");
        client().post("/api/auth/mobile/refresh", Map.of("refreshToken", login.text("refreshToken")));
        return login.body().get("sessionId").asLong();
    }

    private UserSession load(long sessionId) {
        return transactionTemplate.execute(status -> sessions.findById(new SessionId(sessionId)).orElseThrow());
    }
}
