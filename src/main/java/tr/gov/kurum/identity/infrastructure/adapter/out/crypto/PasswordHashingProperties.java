package tr.gov.kurum.identity.infrastructure.adapter.out.crypto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** @param bcryptStrength cost factor; 12 in production, lowered only in tests to keep them fast */
@Validated
@ConfigurationProperties(prefix = "app.password")
public record PasswordHashingProperties(@DefaultValue("12") @Min(4) @Max(31) int bcryptStrength) {
}
