package tr.gov.kurum.identity.domain.model;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.AccountType;
import tr.gov.kurum.common.domain.EmailAddress;

/**
 * Who the account is and what it may do, at the moment a token is issued.
 *
 * @param membershipLevel {@code null} when the account has no valid membership (always for admins)
 */
public record AccessProfile(
        AccountId accountId,
        AccountType type,
        EmailAddress email,
        Set<RoleName> roles,
        Set<PermissionName> permissions,
        MembershipLevel membershipLevel) {

    public AccessProfile {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(email, "email");
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
    }

    public Optional<MembershipLevel> membership() {
        return Optional.ofNullable(membershipLevel);
    }
}
