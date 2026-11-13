package tr.gov.kurum.identity.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import tr.gov.kurum.common.domain.BusinessRuleException;

/**
 * Name of a role stored in the database (e.g. {@code USER}). Names that collide with authorities generated in
 * code are rejected, so a database record can never turn a customer into an admin or grant a membership.
 */
public final class RoleName {

    public static final RoleName ADMIN = new RoleName("ADMIN");
    public static final RoleName SUPER_ADMIN = new RoleName("SUPER_ADMIN");

    private static final Pattern FORMAT = Pattern.compile("^[A-Z][A-Z0-9_]{1,49}$");
    private static final List<String> RESERVED_PREFIXES = List.of("ROLE_", "TYPE_", "MEMBERSHIP_");
    private static final List<String> RESERVED_NAMES = List.of(ADMIN.value, SUPER_ADMIN.value);

    private final String value;

    private RoleName(String value) {
        this.value = value;
    }

    /** For roles coming from storage: validated against format and reserved names. */
    public static RoleName of(String value) {
        if (value == null || !FORMAT.matcher(value).matches()
                || RESERVED_PREFIXES.stream().anyMatch(value::startsWith)
                || RESERVED_NAMES.contains(value)) {
            throw new BusinessRuleException(IdentityErrorKey.INVALID_ROLE_NAME, String.valueOf(value));
        }
        return new RoleName(value);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RoleName that && value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
