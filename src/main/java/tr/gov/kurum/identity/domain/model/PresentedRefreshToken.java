package tr.gov.kurum.identity.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A refresh token presented by a client, as located in storage: either the session's current token or one
 * that was already rotated away ({@code retiredAt} set).
 */
public record PresentedRefreshToken(RefreshTokenHash hash, Instant retiredAt) {

    public PresentedRefreshToken {
        Objects.requireNonNull(hash, "hash");
    }

    public static PresentedRefreshToken current(RefreshTokenHash hash) {
        return new PresentedRefreshToken(hash, null);
    }

    public static PresentedRefreshToken retired(RefreshTokenHash hash, Instant retiredAt) {
        return new PresentedRefreshToken(hash, Objects.requireNonNull(retiredAt, "retiredAt"));
    }

    public boolean isCurrent() {
        return retiredAt == null;
    }
}
