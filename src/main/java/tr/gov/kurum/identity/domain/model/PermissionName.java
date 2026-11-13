package tr.gov.kurum.identity.domain.model;

import java.util.regex.Pattern;
import tr.gov.kurum.common.domain.BusinessRuleException;

/**
 * A permission in {@code resource:action} form (e.g. {@code customer:write}). The lower-case format cannot
 * collide with the upper-case authorities generated in code ({@code ROLE_*}, {@code TYPE_*}, {@code MEMBERSHIP_*}).
 */
public record PermissionName(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[a-z][a-z0-9-]*(:[a-z][a-z0-9-]*)+$");
    private static final int MAX_LENGTH = 100;

    public PermissionName {
        if (value == null || value.length() > MAX_LENGTH || !FORMAT.matcher(value).matches()) {
            throw new BusinessRuleException(IdentityErrorKey.INVALID_PERMISSION_NAME, String.valueOf(value));
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
