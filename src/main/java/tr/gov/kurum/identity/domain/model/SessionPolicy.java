package tr.gov.kurum.identity.domain.model;

import java.time.Duration;
import java.util.Objects;

/**
 * Session lifetime rules.
 *
 * @param refreshTokenTtl     how long a refresh token stays valid after it was issued (sliding)
 * @param rotationGracePeriod a just-rotated token presented again within this window is treated as a
 *                            parallel request from another tab, not as theft
 */
public record SessionPolicy(Duration refreshTokenTtl, Duration rotationGracePeriod) {

    public SessionPolicy {
        Objects.requireNonNull(refreshTokenTtl, "refreshTokenTtl");
        Objects.requireNonNull(rotationGracePeriod, "rotationGracePeriod");
        if (!refreshTokenTtl.isPositive() || rotationGracePeriod.isNegative()) {
            throw new IllegalArgumentException("invalid session policy durations");
        }
    }
}
