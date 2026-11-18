package tr.gov.kurum.customer.infrastructure.adapter.in.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import tr.gov.kurum.common.domain.PermissionCodes;
import tr.gov.kurum.common.web.AuthenticatedAccount;

/**
 * Resource-based authorization for {@code @PreAuthorize("@customerAccess.canModify(authentication, #id)")}:
 * a customer may change only their own profile, an admin needs {@code customer:write}.
 */
@Component("customerAccess")
public class CustomerAccess {

    public boolean canModify(Authentication authentication, long customerId) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedAccount account)) {
            return false;
        }
        // SUPPORT agents only act through an act-as token (type CUSTOMER + actorId); their own token cannot.
        return switch (account.type()) {
            case CUSTOMER -> account.accountId().value() == customerId;
            case ADMIN -> hasAuthority(authentication, PermissionCodes.CUSTOMER_WRITE);
            case SUPPORT -> false;
        };
    }

    private static boolean hasAuthority(Authentication authentication, String authority) {
        for (GrantedAuthority granted : authentication.getAuthorities()) {
            if (authority.equals(granted.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
