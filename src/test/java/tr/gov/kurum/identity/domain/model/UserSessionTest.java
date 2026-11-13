package tr.gov.kurum.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.BusinessRuleException;
import tr.gov.kurum.common.domain.EntityNotFoundException;
import tr.gov.kurum.identity.domain.event.RefreshTokenReuseDetected;

class UserSessionTest {

    private static final Instant T0 = Instant.parse("2026-03-01T10:00:00Z");
    private static final SessionPolicy POLICY = new SessionPolicy(Duration.ofDays(14), Duration.ofSeconds(5));
    private static final AccountId OWNER = new AccountId(7);
    private static final ClientDetails CLIENT = new ClientDetails("JUnit", "10.0.0.1");
    private static final AccountStanding ACTIVE = new AccountStanding(AccountStatus.ACTIVE, Instant.EPOCH);

    private final RefreshTokenHash first = RefreshTokenHash.of("first-raw-token");
    private final RefreshTokenHash second = RefreshTokenHash.of("second-raw-token");
    private final RefreshTokenHash third = RefreshTokenHash.of("third-raw-token");

    private UserSession newSession() {
        return UserSession.start(new SessionId(1), OWNER, CLIENT, first, POLICY, T0);
    }

    @Test
    void refresh_rotates_the_token_and_remembers_the_old_one() {
        UserSession session = newSession();
        Instant now = T0.plusSeconds(60);

        RefreshOutcome outcome = session.refresh(PresentedRefreshToken.current(first), ACTIVE, second,
            new ClientDetails("Other", "10.0.0.2"), POLICY, now);

        assertThat(outcome).isEqualTo(RefreshOutcome.ROTATED);
        assertThat(session.currentTokenHash()).isEqualTo(second);
        assertThat(session.expiresAt()).isEqualTo(now.plus(POLICY.refreshTokenTtl()));
        assertThat(session.retiredTokensSinceLoad())
            .containsExactly(new RetiredRefreshToken(first, now));
        assertThat(session.pullDomainEvents()).isEmpty();
    }

    @Test
    void retired_token_within_grace_period_is_a_concurrent_refresh_and_keeps_the_session() {
        UserSession session = newSession();
        Instant rotatedAt = T0.plusSeconds(60);
        session.refresh(PresentedRefreshToken.current(first), ACTIVE, second, CLIENT, POLICY, rotatedAt);

        RefreshOutcome outcome = session.refresh(PresentedRefreshToken.retired(first, rotatedAt), ACTIVE, third,
            CLIENT, POLICY, rotatedAt.plusSeconds(2));

        assertThat(outcome).isEqualTo(RefreshOutcome.CONCURRENT_REFRESH);
        assertThat(session.isEnded()).isFalse();
        assertThat(session.currentTokenHash()).isEqualTo(second);
    }

    @Test
    void retired_token_after_grace_period_ends_the_session_and_reports_reuse() {
        UserSession session = newSession();
        Instant rotatedAt = T0.plusSeconds(60);
        session.refresh(PresentedRefreshToken.current(first), ACTIVE, second, CLIENT, POLICY, rotatedAt);
        session.pullDomainEvents();

        Instant later = rotatedAt.plusSeconds(6);
        RefreshOutcome outcome = session.refresh(PresentedRefreshToken.retired(first, rotatedAt), ACTIVE, third,
            CLIENT, POLICY, later);

        assertThat(outcome).isEqualTo(RefreshOutcome.TOKEN_INVALID);
        assertThat(session.isEnded()).isTrue();
        assertThat(session.endReason()).contains(SessionEndReason.TOKEN_REUSE_DETECTED);
        assertThat(session.pullDomainEvents())
            .containsExactly(new RefreshTokenReuseDetected(OWNER, new SessionId(1), later));
        // The legitimate holder of the newest token is locked out too: the chain is compromised.
        assertThat(session.refresh(PresentedRefreshToken.current(second), ACTIVE, third, CLIENT, POLICY, later))
            .isEqualTo(RefreshOutcome.TOKEN_INVALID);
    }

    @Test
    void session_started_before_the_accounts_cutoff_is_ended() {
        UserSession session = newSession();
        AccountStanding invalidated = new AccountStanding(AccountStatus.ACTIVE, T0.plusSeconds(1));

        RefreshOutcome outcome = session.refresh(PresentedRefreshToken.current(first), invalidated, second, CLIENT,
            POLICY, T0.plusSeconds(30));

        assertThat(outcome).isEqualTo(RefreshOutcome.SESSION_ENDED);
        assertThat(session.endReason()).contains(SessionEndReason.ACCOUNT_INVALIDATED);
    }

    @Test
    void disabled_or_missing_account_ends_the_session() {
        UserSession session = newSession();

        assertThat(session.refresh(PresentedRefreshToken.current(first), AccountStanding.unavailable(), second,
            CLIENT, POLICY, T0.plusSeconds(1))).isEqualTo(RefreshOutcome.SESSION_ENDED);
    }

    @Test
    void expired_session_cannot_be_refreshed() {
        UserSession session = newSession();

        assertThat(session.refresh(PresentedRefreshToken.current(first), ACTIVE, second, CLIENT, POLICY,
            T0.plus(POLICY.refreshTokenTtl()))).isEqualTo(RefreshOutcome.TOKEN_INVALID);
    }

    @Test
    void logout_with_a_rotated_token_changes_nothing_but_the_current_token_ends_the_session() {
        UserSession session = newSession();
        Instant rotatedAt = T0.plusSeconds(60);
        session.refresh(PresentedRefreshToken.current(first), ACTIVE, second, CLIENT, POLICY, rotatedAt);

        session.logout(PresentedRefreshToken.retired(first, rotatedAt), rotatedAt.plusSeconds(1));
        assertThat(session.isEnded()).isFalse();

        session.logout(PresentedRefreshToken.current(second), rotatedAt.plusSeconds(2));
        assertThat(session.endReason()).contains(SessionEndReason.LOGOUT);
    }

    @Test
    void another_account_cannot_revoke_the_session() {
        UserSession session = newSession();
        AccountId otherAccount = new AccountId(8);

        assertThatThrownBy(() -> session.revokeByOwner(otherAccount, T0))
            .isInstanceOf(EntityNotFoundException.class)
            .extracting(e -> ((EntityNotFoundException) e).errorKey())
            .isEqualTo(IdentityErrorKey.SESSION_NOT_FOUND);
        assertThatThrownBy(() -> session.revokeByAdmin(otherAccount, T0))
            .isInstanceOf(EntityNotFoundException.class);
        assertThat(session.isEnded()).isFalse();

        session.revokeByAdmin(OWNER, T0.plusSeconds(1));
        assertThat(session.endReason()).contains(SessionEndReason.REVOKED_BY_ADMIN);
    }

    @Test
    void refresh_outcomes_map_to_error_keys() {
        RefreshOutcome.ROTATED.requireRotated();
        assertThatThrownBy(RefreshOutcome.CONCURRENT_REFRESH::requireRotated)
            .isInstanceOf(BusinessRuleException.class)
            .extracting(e -> ((BusinessRuleException) e).errorKey())
            .isEqualTo(IdentityErrorKey.REFRESH_IN_PROGRESS);
        assertThatThrownBy(RefreshOutcome.SESSION_ENDED::requireRotated)
            .extracting(e -> ((BusinessRuleException) e).errorKey())
            .isEqualTo(IdentityErrorKey.SESSION_ENDED);
    }
}
