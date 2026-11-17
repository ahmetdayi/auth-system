package tr.gov.kurum.identity.infrastructure.adapter.in.web;

import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.AccountType;
import tr.gov.kurum.common.domain.PermissionCodes;
import tr.gov.kurum.identity.application.port.in.AccountAdministrationUseCase;
import tr.gov.kurum.identity.application.port.in.AccountSummaryView;
import tr.gov.kurum.identity.application.port.in.SessionAdministrationUseCase;
import tr.gov.kurum.identity.application.port.in.SessionView;
import tr.gov.kurum.identity.domain.model.SessionId;

/**
 * Admin operations on accounts and sessions — the identity context's part of the admin console. Two
 * independent checks apply to every method:
 * <ol>
 *   <li>URL rule (SecurityConfig): {@code /api/admin/**} needs TYPE_ADMIN, which only admin accounts get;</li>
 *   <li>{@code @PreAuthorize}: a permission such as {@code customer:write}, or SUPER_ADMIN.</li>
 * </ol>
 * Customer URLs pass {@link AccountType#CUSTOMER} as the expected type and admin URLs {@link AccountType#ADMIN}:
 * all accounts share one id space, so the type check is what keeps {@code /customers/6/sessions} from reaching
 * admin #6.
 */
@RestController
@RequestMapping("/api/admin")
class AccountAdminController {

    private final AccountAdministrationUseCase accounts;
    private final SessionAdministrationUseCase sessions;

    AccountAdminController(AccountAdministrationUseCase accounts, SessionAdministrationUseCase sessions) {
        this.accounts = accounts;
        this.sessions = sessions;
    }

    /** Login data (status, type) for screens that combine several contexts, e.g. the customer list. */
    @GetMapping("/accounts")
    @PreAuthorize("hasAuthority('" + PermissionCodes.CUSTOMER_READ + "')")
    List<AccountSummaryView> accounts(@RequestParam @Size(min = 1, max = AccountAdministrationUseCase.MAX_LOOKUP_IDS)
                                      List<Long> ids) {
        return accounts.findAccounts(ids.stream().map(AccountId::new).collect(Collectors.toSet()));
    }

    /** Ban: the account is disabled and every session and access token of it stops working at once. */
    @PostMapping("/customers/{customerId}/disable")
    @PreAuthorize("hasAuthority('" + PermissionCodes.CUSTOMER_WRITE + "')")
    ResponseEntity<Void> disableCustomer(@PathVariable long customerId) {
        accounts.disableCustomer(new AccountId(customerId));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/customers/{customerId}/enable")
    @PreAuthorize("hasAuthority('" + PermissionCodes.CUSTOMER_WRITE + "')")
    ResponseEntity<Void> enableCustomer(@PathVariable long customerId) {
        accounts.enableCustomer(new AccountId(customerId));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/customers/{customerId}/sessions")
    @PreAuthorize("hasAuthority('" + PermissionCodes.CUSTOMER_READ + "')")
    List<SessionView> customerSessions(@PathVariable long customerId) {
        return sessions.listActiveSessionsOf(new AccountId(customerId), AccountType.CUSTOMER);
    }

    /** Suspicious activity: every refresh token and every access token of the customer, immediately. */
    @DeleteMapping("/customers/{customerId}/sessions")
    @PreAuthorize("hasAuthority('" + PermissionCodes.CUSTOMER_WRITE + "')")
    ResponseEntity<Void> terminateCustomerSessions(@PathVariable long customerId) {
        sessions.terminateAllSessions(new AccountId(customerId), AccountType.CUSTOMER);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/customers/{customerId}/sessions/{sessionId}")
    @PreAuthorize("hasAuthority('" + PermissionCodes.CUSTOMER_WRITE + "')")
    ResponseEntity<Void> revokeCustomerSession(@PathVariable long customerId, @PathVariable long sessionId) {
        sessions.revokeSession(new AccountId(customerId), AccountType.CUSTOMER, new SessionId(sessionId));
        return ResponseEntity.noContent().build();
    }

    /** Other admins' sessions: SUPER_ADMIN only. A standard admin gets 403 even with customer:write. */
    @GetMapping("/admins/{adminId}/sessions")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    List<SessionView> adminSessions(@PathVariable long adminId) {
        return sessions.listActiveSessionsOf(new AccountId(adminId), AccountType.ADMIN);
    }

    @DeleteMapping("/admins/{adminId}/sessions")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    ResponseEntity<Void> terminateAdminSessions(@PathVariable long adminId) {
        sessions.terminateAllSessions(new AccountId(adminId), AccountType.ADMIN);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/admins/{adminId}/sessions/{sessionId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    ResponseEntity<Void> revokeAdminSession(@PathVariable long adminId, @PathVariable long sessionId) {
        sessions.revokeSession(new AccountId(adminId), AccountType.ADMIN, new SessionId(sessionId));
        return ResponseEntity.noContent().build();
    }
}
