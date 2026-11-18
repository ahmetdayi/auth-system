package tr.gov.kurum.identity.application.service;

import java.util.Optional;
import org.springframework.stereotype.Component;
import tr.gov.kurum.common.domain.AccountType;
import tr.gov.kurum.identity.application.port.out.MembershipLevelLookup;
import tr.gov.kurum.identity.application.port.out.RoleRepository;
import tr.gov.kurum.identity.domain.model.AccessProfile;
import tr.gov.kurum.identity.domain.model.MembershipLevel;
import tr.gov.kurum.identity.domain.model.UserAccount;

/**
 * Loads what {@link UserAccount#accessProfile} needs: the account's roles, and for customers the current
 * membership from the membership context. The decision itself stays in the aggregate.
 */
@Component
public class AccessProfileAssembler {

    private final RoleRepository roleRepository;
    private final MembershipLevelLookup membershipLevelLookup;

    public AccessProfileAssembler(RoleRepository roleRepository, MembershipLevelLookup membershipLevelLookup) {
        this.roleRepository = roleRepository;
        this.membershipLevelLookup = membershipLevelLookup;
    }

    public AccessProfile profileOf(UserAccount account) {
        Optional<MembershipLevel> membership = account.is(AccountType.CUSTOMER)
            ? membershipLevelLookup.currentLevelOf(account.id())
            : Optional.empty();
        return account.accessProfile(roleRepository.findAllById(account.roleIds()), membership.orElse(null));
    }
}
