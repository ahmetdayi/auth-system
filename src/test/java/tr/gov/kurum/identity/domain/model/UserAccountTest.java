package tr.gov.kurum.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.AccountType;
import tr.gov.kurum.common.domain.BusinessRuleException;
import tr.gov.kurum.common.domain.EmailAddress;
import tr.gov.kurum.identity.domain.event.AccountSessionsInvalidated;
import tr.gov.kurum.identity.domain.event.CustomerAccountOpened;

class UserAccountTest {

    private static final Instant T0 = Instant.parse("2026-03-01T10:00:00Z");
    private static final RoleId USER_ROLE = new RoleId(1);
    private static final AccountId ID = new AccountId(10);

    private UserAccount newCustomer() {
        return UserAccount.openCustomerAccount(ID, EmailAddress.of("ayse@example.com"),
            new PasswordHash("$2a$04$hash"), USER_ROLE, "Ayşe", "Yılmaz", T0);
    }

    private static UserAccount admin(AdminLevel level, Set<PermissionName> permissions) {
        return UserAccount.restore(new UserAccountSnapshot(new AccountId(20), 3, EmailAddress.of("admin@example.com"),
            new PasswordHash("$2a$04$hash"), AccountType.ADMIN, level, AccountStatus.ACTIVE, Instant.EPOCH,
            Set.of(), permissions, T0));
    }

    @Test
    void opening_a_customer_account_publishes_the_event_the_customer_context_builds_the_profile_from() {
        UserAccount account = newCustomer();

        assertThat(account.isNew()).isTrue();
        assertThat(account.is(AccountType.CUSTOMER)).isTrue();
        assertThat(account.standing().isActive()).isTrue();
        assertThat(account.standing().acceptsSessionStartedAt(T0)).isTrue();
        assertThat(account.pullDomainEvents()).containsExactly(
            new CustomerAccountOpened(ID, "ayse@example.com", "Ayşe", "Yılmaz", T0));
    }

    @Test
    void the_opened_event_does_not_print_personal_data() {
        assertThat(new CustomerAccountOpened(ID, "ayse@example.com", "Ayşe", "Yılmaz", T0).toString())
            .doesNotContain("ayse", "Ayşe", "Yılmaz");
    }

    @Test
    void changing_the_password_invalidates_every_earlier_session() {
        UserAccount account = newCustomer();
        account.pullDomainEvents();
        Instant changedAt = T0.plusSeconds(3600);

        account.changePassword(new PasswordHash("$2a$04$other"), changedAt);

        assertThat(account.standing().acceptsSessionStartedAt(changedAt.minusMillis(1))).isFalse();
        assertThat(account.standing().acceptsSessionStartedAt(changedAt)).isFalse();   // fail-closed
        assertThat(account.standing().acceptsSessionStartedAt(changedAt.plusMillis(1))).isTrue();
        assertThat(account.pullDomainEvents()).containsExactly(
            new AccountSessionsInvalidated(ID, SessionInvalidationCause.PASSWORD_CHANGED, changedAt));
    }

    @Test
    void disabling_is_idempotent() {
        UserAccount account = newCustomer();
        account.disable(T0.plusSeconds(1));
        account.pullDomainEvents();

        account.disable(T0.plusSeconds(2));

        assertThat(account.pullDomainEvents()).isEmpty();
        assertThat(account.sessionsValidAfter()).isEqualTo(T0.plusSeconds(1));
        assertThat(account.standing().isActive()).isFalse();
    }

    @Test
    void a_customer_gets_assigned_roles_and_the_membership_level_it_is_given() {
        UserAccount account = newCustomer();
        Role user = Role.restore(USER_ROLE, 0, RoleName.of("USER"), Set.of(new PermissionName("profile:read")));
        Role notAssigned = Role.restore(new RoleId(2), 0, RoleName.of("PREMIUM_READER"),
            Set.of(new PermissionName("content:premium:read")));

        AccessProfile withGold = account.accessProfile(List.of(user, notAssigned), new MembershipLevel("GOLD"));
        AccessProfile without = account.accessProfile(List.of(user, notAssigned), null);

        assertThat(withGold.roles()).containsExactly(RoleName.of("USER"));
        assertThat(withGold.permissions()).containsExactly(new PermissionName("profile:read"));
        assertThat(withGold.membership()).contains(new MembershipLevel("GOLD"));
        assertThat(without.membership()).isEmpty();
    }

    @Test
    void admin_roles_come_from_type_and_level_and_admins_never_carry_a_membership() {
        UserAccount standard = admin(AdminLevel.STANDARD, Set.of(new PermissionName("customer:read")));
        UserAccount superAdmin = admin(AdminLevel.SUPER, Set.of());

        AccessProfile standardProfile = standard.accessProfile(List.of(), new MembershipLevel("GOLD"));
        AccessProfile superProfile = superAdmin.accessProfile(List.of(), null);

        assertThat(standardProfile.roles()).containsExactly(RoleName.ADMIN);
        assertThat(standardProfile.permissions()).containsExactly(new PermissionName("customer:read"));
        assertThat(standardProfile.membership()).isEmpty();
        assertThat(superProfile.roles()).containsExactlyInAnyOrder(RoleName.ADMIN, RoleName.SUPER_ADMIN);
    }

    @Test
    void only_admins_have_a_level() {
        assertThatThrownBy(() -> UserAccount.restore(new UserAccountSnapshot(ID, 0, EmailAddress.of("x@example.com"),
            new PasswordHash("$2a$04$hash"), AccountType.CUSTOMER, AdminLevel.SUPER, AccountStatus.ACTIVE,
            Instant.EPOCH, Set.of(), Set.of(), T0)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> admin(null, Set.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void membership_level_names_are_validated() {
        assertThatThrownBy(() -> new MembershipLevel("gold"))
            .isInstanceOf(BusinessRuleException.class)
            .extracting(e -> ((BusinessRuleException) e).errorKey())
            .isEqualTo(IdentityErrorKey.INVALID_MEMBERSHIP_LEVEL);
        assertThatThrownBy(() -> new MembershipLevel("GOLD_PLUS")).isInstanceOf(BusinessRuleException.class);
    }
}
