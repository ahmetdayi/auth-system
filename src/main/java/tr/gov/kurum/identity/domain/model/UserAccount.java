package tr.gov.kurum.identity.domain.model;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.AccountType;
import tr.gov.kurum.common.domain.AggregateRoot;
import tr.gov.kurum.common.domain.EmailAddress;
import tr.gov.kurum.identity.domain.event.AccountSessionsInvalidated;
import tr.gov.kurum.identity.domain.event.CustomerAccountOpened;

/**
 * Everything needed to decide whether someone may log in and what they may do — and nothing else. Names,
 * addresses, membership and department belong to other bounded contexts, keyed by the same {@link AccountId}.
 *
 * <p>Customers and admins are the same kind of thing here (credentials, status, roles), so they are one
 * aggregate with a type; only admins have a privilege level.</p>
 */
public final class UserAccount extends AggregateRoot {

    private final AccountId id;
    private final EmailAddress email;
    private final AccountType type;
    private final AdminLevel adminLevel;
    private final Set<RoleId> roleIds;
    private final Set<PermissionName> directPermissions;
    private final Instant createdAt;
    private PasswordHash passwordHash;
    private AccountStatus status;
    private Instant sessionsValidAfter;

    private UserAccount(UserAccountSnapshot state) {
        super(state.version());
        this.id = Objects.requireNonNull(state.id(), "id");
        this.email = Objects.requireNonNull(state.email(), "email");
        this.type = Objects.requireNonNull(state.type(), "type");
        if ((type == AccountType.ADMIN) != (state.adminLevel() != null)) {
            throw new IllegalArgumentException("admins, and only admins, have a level");
        }
        this.adminLevel = state.adminLevel();
        this.passwordHash = Objects.requireNonNull(state.passwordHash(), "passwordHash");
        this.status = Objects.requireNonNull(state.status(), "status");
        this.sessionsValidAfter = Objects.requireNonNull(state.sessionsValidAfter(), "sessionsValidAfter");
        this.roleIds = new LinkedHashSet<>(state.roleIds());
        this.directPermissions = new LinkedHashSet<>(state.directPermissions());
        this.createdAt = Objects.requireNonNull(state.createdAt(), "createdAt");
    }

    /**
     * Opens a customer account. The name is not kept here: it travels in the {@link CustomerAccountOpened}
     * event to the customer context, which owns it.
     */
    public static UserAccount openCustomerAccount(AccountId id, EmailAddress email, PasswordHash passwordHash,
                                                  RoleId defaultRole, String firstName, String lastName,
                                                  Instant now) {
        Objects.requireNonNull(defaultRole, "defaultRole");
        UserAccount account = new UserAccount(new UserAccountSnapshot(id, UNSAVED_VERSION, email, passwordHash,
            AccountType.CUSTOMER, null, AccountStatus.ACTIVE, Instant.EPOCH, Set.of(defaultRole), Set.of(), now));
        account.registerEvent(new CustomerAccountOpened(id, email.value(), firstName, lastName, now));
        return account;
    }

    public static UserAccount restore(UserAccountSnapshot snapshot) {
        return new UserAccount(snapshot);
    }

    // --- behaviour -------------------------------------------------------------------------------

    /** Changing the password ends every existing session of the account. */
    public void changePassword(PasswordHash newPasswordHash, Instant now) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash, "newPasswordHash");
        invalidateAllSessions(SessionInvalidationCause.PASSWORD_CHANGED, now);
    }

    /** Idempotent: disabling an already disabled account changes nothing. */
    public void disable(Instant now) {
        if (status == AccountStatus.DISABLED) {
            return;
        }
        this.status = AccountStatus.DISABLED;
        invalidateAllSessions(SessionInvalidationCause.ACCOUNT_DISABLED, now);
    }

    public void enable() {
        this.status = AccountStatus.ACTIVE;
    }

    /** Sessions started up to {@code now} can no longer be refreshed; access tokens are revoked by a listener. */
    public void invalidateAllSessions(SessionInvalidationCause cause, Instant now) {
        Objects.requireNonNull(cause, "cause");
        this.sessionsValidAfter = Objects.requireNonNull(now, "now");
        registerEvent(new AccountSessionsInvalidated(id, cause, now));
    }

    // --- queries ---------------------------------------------------------------------------------

    /**
     * What the account may do. Admin roles are produced here from the account type and level, never read
     * from the role table (whose names may not even be ADMIN or SUPER_ADMIN).
     *
     * @param assignedRoles   the roles referenced by {@link #roleIds()}, loaded by the caller
     * @param membershipLevel the customer's currently valid membership, from the membership context; ignored
     *                        for admins; {@code null} when there is none
     */
    public AccessProfile accessProfile(Collection<Role> assignedRoles, MembershipLevel membershipLevel) {
        Set<RoleName> roles = new LinkedHashSet<>();
        Set<PermissionName> permissions = new LinkedHashSet<>(directPermissions);
        for (Role role : assignedRoles) {
            if (roleIds.contains(role.id())) {
                roles.add(role.name());
                permissions.addAll(role.permissions());
            }
        }
        if (type == AccountType.ADMIN) {
            roles.add(RoleName.ADMIN);
            if (adminLevel == AdminLevel.SUPER) {
                roles.add(RoleName.SUPER_ADMIN);
            }
        }
        MembershipLevel membership = type == AccountType.CUSTOMER ? membershipLevel : null;
        return new AccessProfile(id, type, email, roles, permissions, membership);
    }

    public AccountStanding standing() {
        return new AccountStanding(status, sessionsValidAfter);
    }

    public boolean is(AccountType expected) {
        return type == expected;
    }

    // --- accessors (no setters) ---------------------------------------------------------------------

    public AccountId id() {
        return id;
    }

    public EmailAddress email() {
        return email;
    }

    public AccountType type() {
        return type;
    }

    public Optional<AdminLevel> adminLevel() {
        return Optional.ofNullable(adminLevel);
    }

    public PasswordHash passwordHash() {
        return passwordHash;
    }

    public AccountStatus status() {
        return status;
    }

    public Instant sessionsValidAfter() {
        return sessionsValidAfter;
    }

    public Set<RoleId> roleIds() {
        return Set.copyOf(roleIds);
    }

    public Set<PermissionName> directPermissions() {
        return Set.copyOf(directPermissions);
    }

    public Instant createdAt() {
        return createdAt;
    }
}
