package tr.gov.kurum.membership.application.port.in;

import tr.gov.kurum.common.domain.AccountId;

/** Admin operations. Authorization is enforced by the web adapter. */
public interface MembershipAdministrationUseCase {

    /** Takes effect in the customer's token at their next refresh. */
    void grant(GrantMembershipCommand command);

    void cancel(AccountId member);
}
