package tr.gov.kurum.identity.application.port.in;

import java.time.Instant;
import java.util.Objects;

/** Identifier ({@code jti}) and expiry of a verified access token; enough to blacklist it until it expires. */
public record RevocableAccessToken(String tokenId, Instant expiresAt) {

    public RevocableAccessToken {
        Objects.requireNonNull(tokenId, "tokenId");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
