package tr.gov.kurum.identity.infrastructure.adapter.out.token;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param jwtSecret                        Base64, at least 256 bits; required unless the ephemeral fallback is on
 * @param generateEphemeralSecretIfMissing local development only: generate a random key at startup
 *                                         (tokens become invalid on restart). Never enable in production.
 */
@Validated
@ConfigurationProperties(prefix = "app.token")
public record TokenProperties(
        String jwtSecret,
        boolean generateEphemeralSecretIfMissing,
        @NotBlank String issuer,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl,
        @NotNull Duration rotationGracePeriod) {
}
