package tr.gov.kurum.identity.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import tr.gov.kurum.common.domain.BusinessRuleException;

/**
 * SHA-256 of an opaque refresh token. Only the hash is stored. SHA-256 is adequate here because the input is a
 * 256-bit random value; user passwords, which have low entropy, are hashed with BCrypt instead.
 */
public record RefreshTokenHash(String value) {

    private static final int MAX_RAW_LENGTH = 200;

    public RefreshTokenHash {
        Objects.requireNonNull(value, "value");
    }

    public static RefreshTokenHash of(String rawToken) {
        return tryOf(rawToken).orElseThrow(() -> new BusinessRuleException(IdentityErrorKey.REFRESH_TOKEN_INVALID));
    }

    /** Lenient variant for logout and "is this my current session" checks: malformed input is simply absent. */
    public static Optional<RefreshTokenHash> tryOf(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > MAX_RAW_LENGTH) {
            return Optional.empty();
        }
        return Optional.of(hash(rawToken));
    }

    private static RefreshTokenHash hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return new RefreshTokenHash(Base64.getUrlEncoder().withoutPadding().encodeToString(digest));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", e);
        }
    }

    @Override
    public String toString() {
        return "RefreshTokenHash[" + value.substring(0, Math.min(8, value.length())) + "...]";
    }
}
