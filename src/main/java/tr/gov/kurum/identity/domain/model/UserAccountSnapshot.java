package tr.gov.kurum.identity.domain.model;

import java.time.Instant;
import java.util.Set;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.AccountType;
import tr.gov.kurum.common.domain.EmailAddress;

/**
 * Stored state of a {@link UserAccount}, used to restore it. Persistence adapters build this instead of
 * calling setters on the aggregate.
 *
 * @param adminLevel {@code null} for customers
 */
public record UserAccountSnapshot(
        AccountId id,
        long version,
        EmailAddress email,
        PasswordHash passwordHash,
        AccountType type,
        AdminLevel adminLevel,
        AccountStatus status,
        Instant sessionsValidAfter,
        Set<RoleId> roleIds,
        Set<PermissionName> directPermissions,
        Instant createdAt) {

    public UserAccountSnapshot {
        roleIds = Set.copyOf(roleIds);
        directPermissions = Set.copyOf(directPermissions);
    }
}
