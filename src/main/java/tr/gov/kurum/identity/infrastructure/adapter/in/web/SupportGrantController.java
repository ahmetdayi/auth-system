package tr.gov.kurum.identity.infrastructure.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.AccountType;
import tr.gov.kurum.common.web.AuthenticatedAccount;
import tr.gov.kurum.common.web.CurrentAccount;
import tr.gov.kurum.identity.application.port.in.SupportGrantUseCase;
import tr.gov.kurum.identity.application.port.in.SupportGrantView;
import tr.gov.kurum.identity.application.port.in.SupportSessionView;
import tr.gov.kurum.identity.application.port.out.IssuedAccessToken;
import tr.gov.kurum.identity.domain.model.SupportGrantExtensionId;
import tr.gov.kurum.identity.domain.model.SupportGrantId;
import tr.gov.kurum.identity.domain.model.SupportScopes;

/**
 * Consent-based support grants. The agent (TYPE_SUPPORT, own token) asks and extends; the customer
 * (TYPE_CUSTOMER, own token) decides. Approving sets the agent's {@code support_access_token} cookie
 * (written on the approving browser — for the console demo the customer approve is done in the customer's
 * browser; the agent re-fetches session / the cookie is also set when the agent polls after approval via
 * {@code GET /api/support/session} which only reads). For demo, after customer approve the agent calls
 * {@code POST /api/support/grants/{id}/activate} is not used — cookie is issued to whoever calls approve.
 *
 * <p>Console flow: customer approves on their session (cookie lands on customer browser — not useful).
 * Better demo: agent requests; customer approves; <strong>agent</strong> then calls
 * {@code POST /api/support/grants/{id}/claim} to receive the cookie. See {@link #claim}.</p>
 */
@RestController
@RequestMapping("/api/support")
class SupportGrantController {

    private static final String SUPPORT_ONLY = "hasAuthority('TYPE_SUPPORT')";
    private static final String CUSTOMER_ONLY = "hasAuthority('TYPE_CUSTOMER')";
    private static final String SUPPORT_OR_CUSTOMER = "hasAnyAuthority('TYPE_SUPPORT', 'TYPE_CUSTOMER')";

    private final SupportGrantUseCase grants;
    private final SupportAccessCookieService supportCookies;

    SupportGrantController(SupportGrantUseCase grants, SupportAccessCookieService supportCookies) {
        this.grants = grants;
        this.supportCookies = supportCookies;
    }

    @GetMapping("/session")
    SupportSessionView session(HttpServletRequest request) {
        return supportCookies.currentSession(request);
    }

    /**
     * Soft leave: drops the act-as cookie (and revokes that JWT) but keeps the grant ACTIVE.
     * Agent can {@code claim} again later. Uses the agent's own access cookie (not the support cookie).
     */
    @PostMapping("/session/leave")
    @PreAuthorize(SUPPORT_ONLY)
    SupportSessionView leaveSession(@CurrentAccount AuthenticatedAccount account,
                                    HttpServletRequest request, HttpServletResponse response) {
        account.requireSupport();
        supportCookies.clear(request, response);
        return supportCookies.currentSession(request);
    }

    /** Selectable grant scopes (whitelist). Agent picks a subset or all before requesting. */
    @GetMapping("/scopes")
    @PreAuthorize(SUPPORT_OR_CUSTOMER)
    List<SupportScopeItem> scopes() {
        return SupportScopes.CATALOG.stream()
            .map(e -> new SupportScopeItem(e.code(), e.label(), e.description()))
            .toList();
    }

    // --- agent ----------------------------------------------------------------------------------

    @PostMapping("/grants")
    @PreAuthorize(SUPPORT_ONLY)
    ResponseEntity<SupportGrantView> request(@CurrentAccount AuthenticatedAccount account,
                                             @RequestBody @Valid RequestGrantRequest body) {
        Set<String> scopes = body.scopes() == null ? null : new LinkedHashSet<>(body.scopes());
        SupportGrantView view = grants.request(account.requireSupport(), new AccountId(body.customerId()), scopes);
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    @GetMapping("/grants/mine")
    @PreAuthorize(SUPPORT_ONLY)
    List<SupportGrantView> mine(@CurrentAccount AuthenticatedAccount account) {
        return grants.mine(account.requireSupport());
    }

    /**
     * After the customer has approved, the agent claims the act-as cookie (browser) and receives the same
     * JWT in the body (Bearer / scenario clients). Idempotent while ACTIVE / PENDING_EXTENSION.
     */
    @PostMapping("/grants/{id}/claim")
    @PreAuthorize(SUPPORT_ONLY)
    ClaimResponse claim(@CurrentAccount AuthenticatedAccount account, @PathVariable long id,
                        HttpServletRequest request, HttpServletResponse response) {
        AccountId agentId = account.requireSupport();
        SupportGrantView view = grants.detail(agentId, AccountType.SUPPORT, new SupportGrantId(id));
        if (!"ACTIVE".equals(view.status()) && !"PENDING_EXTENSION".equals(view.status())) {
            return new ClaimResponse(view, null, null);
        }
        IssuedAccessToken issued = supportCookies.issueFor(view, agentId, request, response);
        return new ClaimResponse(view, issued.token(), issued.expiresAt());
    }

    @PostMapping("/grants/{id}/extensions")
    @PreAuthorize(SUPPORT_ONLY)
    ResponseEntity<SupportGrantView> requestExtension(@CurrentAccount AuthenticatedAccount account,
                                                      @PathVariable long id) {
        SupportGrantView view = grants.requestExtension(account.requireSupport(), new SupportGrantId(id));
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    // --- customer -------------------------------------------------------------------------------

    @GetMapping("/grants/incoming")
    @PreAuthorize(CUSTOMER_ONLY)
    List<SupportGrantView> incoming(@CurrentAccount AuthenticatedAccount account) {
        return grants.incoming(ownCustomer(account));
    }

    @GetMapping("/grants/history")
    @PreAuthorize(CUSTOMER_ONLY)
    List<SupportGrantView> history(@CurrentAccount AuthenticatedAccount account) {
        return grants.history(ownCustomer(account));
    }

    @PostMapping("/grants/{id}/approve")
    @PreAuthorize(CUSTOMER_ONLY)
    SupportGrantView approve(@CurrentAccount AuthenticatedAccount account, @PathVariable long id) {
        return grants.approve(ownCustomer(account), new SupportGrantId(id));
    }

    @PostMapping("/grants/{id}/deny")
    @PreAuthorize(CUSTOMER_ONLY)
    SupportGrantView deny(@CurrentAccount AuthenticatedAccount account, @PathVariable long id) {
        return grants.deny(ownCustomer(account), new SupportGrantId(id));
    }

    @PostMapping("/grants/{id}/extensions/{extId}/approve")
    @PreAuthorize(CUSTOMER_ONLY)
    SupportGrantView approveExtension(@CurrentAccount AuthenticatedAccount account, @PathVariable long id,
                                      @PathVariable long extId) {
        return grants.approveExtension(ownCustomer(account), new SupportGrantId(id),
            new SupportGrantExtensionId(extId));
    }

    @PostMapping("/grants/{id}/extensions/{extId}/deny")
    @PreAuthorize(CUSTOMER_ONLY)
    SupportGrantView denyExtension(@CurrentAccount AuthenticatedAccount account, @PathVariable long id,
                                   @PathVariable long extId) {
        return grants.denyExtension(ownCustomer(account), new SupportGrantId(id),
            new SupportGrantExtensionId(extId));
    }

    // --- both parties ---------------------------------------------------------------------------

    @GetMapping("/grants/{id}")
    @PreAuthorize(SUPPORT_OR_CUSTOMER)
    SupportGrantView detail(@CurrentAccount AuthenticatedAccount account, @PathVariable long id) {
        Party party = Party.of(account);
        return grants.detail(party.id(), party.type(), new SupportGrantId(id));
    }

    @PostMapping("/grants/{id}/end")
    @PreAuthorize(SUPPORT_OR_CUSTOMER)
    SupportGrantView end(@CurrentAccount AuthenticatedAccount account, @PathVariable long id,
                         HttpServletRequest request, HttpServletResponse response) {
        Party party = Party.of(account);
        SupportGrantView view = grants.end(party.id(), party.type(), new SupportGrantId(id));
        supportCookies.clear(request, response);
        return view;
    }

    /** A customer decision is only valid with the customer's own token, never from inside an act-as session. */
    private static AccountId ownCustomer(AuthenticatedAccount account) {
        account.refuseIfSupportSession();
        return account.requireCustomer();
    }

    /**
     * Who is acting on a grant: inside an act-as session the token belongs to the customer, but the person
     * is the agent, so the grant is seen from the agent's side.
     */
    private record Party(AccountId id, AccountType type) {

        static Party of(AuthenticatedAccount account) {
            if (account.isSupportSession()) {
                return new Party(account.actorId(), AccountType.SUPPORT);
            }
            return new Party(account.accountId(), account.type());
        }
    }

    record SupportScopeItem(String code, String label, String description) {
    }

    record RequestGrantRequest(
            @NotNull @Positive Long customerId,
            @Size(max = 20) List<@NotBlank @Size(max = 100) String> scopes) {
    }

    record ClaimResponse(SupportGrantView grant, String supportAccessToken, Instant supportAccessExpiresAt) {

        @Override
        public String toString() {
            return "ClaimResponse[grant=" + grant + ", supportAccessToken=PROTECTED, supportAccessExpiresAt="
                + supportAccessExpiresAt + "]";
        }
    }
}
