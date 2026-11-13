package tr.gov.kurum.identity.infrastructure.adapter.out.crypto;

import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import tr.gov.kurum.identity.application.port.out.PasswordHasher;
import tr.gov.kurum.identity.domain.model.PasswordHash;
import tr.gov.kurum.identity.domain.model.PlainTextPassword;

@Component
@EnableConfigurationProperties(PasswordHashingProperties.class)
class BCryptPasswordHasher implements PasswordHasher {

    private static final String TIMING_EQUALIZER_INPUT = "timing-equalizer-input";

    private final BCryptPasswordEncoder encoder;
    private final String dummyHash;

    BCryptPasswordHasher(PasswordHashingProperties properties) {
        this.encoder = new BCryptPasswordEncoder(properties.bcryptStrength());
        // Same cost as real hashes, so a lookup miss costs as much as a wrong password.
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    @Override
    public PasswordHash hash(PlainTextPassword password) {
        if (!password.fitsHashingLimit()) {
            throw new IllegalArgumentException("password exceeds the BCrypt input limit");
        }
        return new PasswordHash(encoder.encode(password.value()));
    }

    @Override
    public boolean verify(PlainTextPassword password, PasswordHash storedHash) {
        if (!password.fitsHashingLimit()) {
            // Never compare inputs BCrypt would truncate (CVE-2025-22228); still spend the same time.
            encoder.matches(TIMING_EQUALIZER_INPUT, dummyHash);
            return false;
        }
        if (storedHash == null) {
            encoder.matches(password.value(), dummyHash);
            return false;
        }
        return encoder.matches(password.value(), storedHash.value());
    }
}
