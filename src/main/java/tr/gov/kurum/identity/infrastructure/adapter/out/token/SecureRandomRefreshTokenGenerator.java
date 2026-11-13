package tr.gov.kurum.identity.infrastructure.adapter.out.token;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;
import tr.gov.kurum.identity.application.port.out.RefreshTokenGenerator;

/** 256 random bits, Base64URL: opaque and unguessable. */
@Component
class SecureRandomRefreshTokenGenerator implements RefreshTokenGenerator {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
