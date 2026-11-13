package tr.gov.kurum.common.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalized e-mail address. In the shared kernel because several contexts hold one (login address,
 * contact address) and the rules must be the same everywhere.
 *
 * <p>Normalized (trimmed, lower case). {@link Locale#ROOT} is mandatory: with a Turkish default
 * locale {@code "I".toLowerCase()} yields a dotless {@code "ı"}, which would make the account unreachable.
 * Only ASCII addresses are accepted, which keeps the database {@code lower(email)} check consistent.
 */
public record EmailAddress(String value) {

    private static final int MAX_LENGTH = 254;
    private static final Pattern FORMAT =
        Pattern.compile("^[a-z0-9._%+-]+@[a-z0-9-]+(\\.[a-z0-9-]+)*\\.[a-z]{2,}$");

    public EmailAddress {
        if (value == null) {
            throw new BusinessRuleException(CommonErrorKey.INVALID_EMAIL);
        }
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.length() > MAX_LENGTH || !FORMAT.matcher(value).matches()) {
            throw new BusinessRuleException(CommonErrorKey.INVALID_EMAIL);
        }
    }

    public static EmailAddress of(String rawValue) {
        return new EmailAddress(rawValue);
    }
}
