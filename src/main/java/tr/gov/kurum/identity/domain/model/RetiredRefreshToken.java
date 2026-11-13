package tr.gov.kurum.identity.domain.model;

import java.time.Instant;
import java.util.Objects;

/** A refresh token replaced by rotation. Kept until the session expires so reuse can be detected. */
public record RetiredRefreshToken(RefreshTokenHash hash, Instant retiredAt) {

    public RetiredRefreshToken {
        Objects.requireNonNull(hash, "hash");
        Objects.requireNonNull(retiredAt, "retiredAt");
    }
}
