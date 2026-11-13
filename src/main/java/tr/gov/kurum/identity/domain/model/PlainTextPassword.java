package tr.gov.kurum.identity.domain.model;

import java.nio.charset.StandardCharsets;
import tr.gov.kurum.common.domain.BusinessRuleException;

/**
 * A password as typed by the user. Never logged: {@link #toString()} is masked.
 * BCrypt only uses the first 72 <em>bytes</em> (CVE-2025-22228), and Turkish letters take two bytes in UTF-8,
 * so the limit is checked in bytes, not characters.
 */
public final class PlainTextPassword {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_BYTES = 72;

    private final String value;

    private PlainTextPassword(String value) {
        if (value == null || value.isEmpty()) {
            throw new BusinessRuleException(IdentityErrorKey.PASSWORD_REQUIRED);
        }
        this.value = value;
    }

    /** For login and "current password" checks: no policy, any non-empty input is compared. */
    public static PlainTextPassword forVerification(String rawValue) {
        return new PlainTextPassword(rawValue);
    }

    /** For registration and password change: enforces the password policy. */
    public static PlainTextPassword forNewPassword(String rawValue) {
        PlainTextPassword password = new PlainTextPassword(rawValue);
        if (rawValue.codePointCount(0, rawValue.length()) < MIN_LENGTH) {
            throw new BusinessRuleException(IdentityErrorKey.PASSWORD_TOO_SHORT, MIN_LENGTH);
        }
        if (!password.fitsHashingLimit()) {
            throw new BusinessRuleException(IdentityErrorKey.PASSWORD_TOO_LONG, MAX_BYTES);
        }
        return password;
    }

    public boolean fitsHashingLimit() {
        return value.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
    }

    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return "PlainTextPassword[PROTECTED]";
    }
}
