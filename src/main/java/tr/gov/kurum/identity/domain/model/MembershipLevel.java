package tr.gov.kurum.identity.domain.model;

import java.util.regex.Pattern;
import tr.gov.kurum.common.domain.BusinessRuleException;

/**
 * A customer's membership as far as authorization is concerned: only its name, which becomes the
 * {@code MEMBERSHIP_<NAME>} authority. The identity context neither knows nor orders the tiers; the
 * membership context owns them (and contributes their ordering to the authority hierarchy).
 */
public record MembershipLevel(String name) {

    private static final Pattern FORMAT = Pattern.compile("^[A-Z]{1,20}$");

    public MembershipLevel {
        if (name == null || !FORMAT.matcher(name).matches()) {
            throw new BusinessRuleException(IdentityErrorKey.INVALID_MEMBERSHIP_LEVEL, String.valueOf(name));
        }
    }
}
