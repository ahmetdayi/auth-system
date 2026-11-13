package tr.gov.kurum.identity.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * The part of an account that decides whether its sessions may continue: status and the instant before
 * which every session is considered invalidated (password change, ban, logout everywhere).
 */
public record AccountStanding(AccountStatus status, Instant sessionsValidAfter) {

    public AccountStanding {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(sessionsValidAfter, "sessionsValidAfter");
    }

    /** Standing used when the owning account no longer exists: rejects every session. */
    public static AccountStanding unavailable() {
        return new AccountStanding(AccountStatus.DISABLED, Instant.EPOCH);
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    /**
     * A session started at the very instant of an invalidation is rejected as well (fail-closed): with
     * millisecond timestamps, "same instant" cannot tell before from after.
     */
    public boolean acceptsSessionStartedAt(Instant startedAt) {
        return isActive() && startedAt.isAfter(sessionsValidAfter);
    }
}
