package tr.gov.kurum.customer.infrastructure.adapter.in.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import tr.gov.kurum.common.web.AuthenticatedAccount;

/**
 * Resource-based authorization for {@code @PreAuthorize("@customerAccess.canModify(authentication, #id)")}:
 * a customer may change only their own profile, an admin needs {@code customer:write}.
 */
@Component("customerAccess")
public class CustomerAccess {

    static final String CUSTOMER_WRITE = "customer:write";

    public boolean canModify(Authentication authentication, long customerId) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedAccount account)) {
            return false;
        }
        // A new account type does not compile here until someone decides what it may do.
        return switch (account.type()) {
            case CUSTOMER -> account.accountId().value() == customerId;
            case ADMIN -> hasAuthority(authentication, CUSTOMER_WRITE);
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
