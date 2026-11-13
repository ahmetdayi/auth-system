package tr.gov.kurum.identity.infrastructure.adapter.out.token;

import java.util.ArrayList;
import java.util.List;
import tr.gov.kurum.common.domain.AccountType;
import tr.gov.kurum.identity.domain.model.AccessProfile;
import tr.gov.kurum.identity.domain.model.PermissionName;
import tr.gov.kurum.identity.domain.model.RoleName;

/**
 * Converts an {@link AccessProfile} into Spring Security authority strings. This is the only place that knows
 * the prefixes. {@code TYPE_*} and {@code MEMBERSHIP_*} are produced here from typed data and can never come
 * from the role table (role and permission names are validated against these prefixes).
 */
public final class AuthorityNames {

    public static final String TYPE_PREFIX = "TYPE_";
    public static final String ROLE_PREFIX = "ROLE_";
    public static final String MEMBERSHIP_PREFIX = "MEMBERSHIP_";

    private AuthorityNames() {
    }

    public static String typeAuthority(AccountType type) {
        return TYPE_PREFIX + type.name();
    }

    public static List<String> of(AccessProfile profile) {
        List<String> authorities = new ArrayList<>();
        authorities.add(typeAuthority(profile.type()));
        for (RoleName role : profile.roles()) {
            authorities.add(ROLE_PREFIX + role.value());
        }
        for (PermissionName permission : profile.permissions()) {
            authorities.add(permission.value());
        }
        profile.membership().ifPresent(level -> authorities.add(MEMBERSHIP_PREFIX + level.name()));
        return List.copyOf(authorities);
    }
}
