package tr.gov.kurum.identity.application.port.out;

import java.time.Instant;
import java.util.Objects;

public record IssuedAccessToken(String token, Instant expiresAt) {

    public IssuedAccessToken {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    @Override
    public String toString() {
        return "IssuedAccessToken[token=PROTECTED, expiresAt=" + expiresAt + "]";
    }
}
