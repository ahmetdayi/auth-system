package tr.gov.kurum.identity.infrastructure.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.PermissionCodes;
import tr.gov.kurum.identity.application.port.in.SupportGrantView;
import tr.gov.kurum.identity.application.port.in.SupportSessionView;
import tr.gov.kurum.identity.application.port.out.AccessTokenIssuer;
import tr.gov.kurum.identity.application.port.out.AccessTokenRevocationStore;
import tr.gov.kurum.identity.application.port.out.IssuedAccessToken;
import tr.gov.kurum.identity.application.port.out.UserAccountRepository;
import tr.gov.kurum.identity.application.service.AccessProfileAssembler;
import tr.gov.kurum.identity.domain.model.SessionId;
import tr.gov.kurum.identity.domain.model.SupportGrantId;
import tr.gov.kurum.identity.infrastructure.adapter.in.web.security.AuthCookies;
import tr.gov.kurum.identity.infrastructure.adapter.out.token.AuthorityNames;
import tr.gov.kurum.identity.infrastructure.adapter.out.token.JwtAccessTokenService;
import tr.gov.kurum.identity.infrastructure.adapter.out.token.VerifiedAccessToken;

/** Issues, refreshes and clears the act-as {@code support_access_token} cookie around grant lifecycle events. */
@Component
class SupportAccessCookieService {

    private final AccessTokenIssuer tokens;
    private final JwtAccessTokenService jwt;
    private final AccessTokenRevocationStore revocation;
    private final AuthCookies cookies;
    private final UserAccountRepository accounts;
    private final AccessProfileAssembler accessProfiles;
    private final Clock clock;

    SupportAccessCookieService(AccessTokenIssuer tokens, JwtAccessTokenService jwt,
                               AccessTokenRevocationStore revocation, AuthCookies cookies,
                               UserAccountRepository accounts, AccessProfileAssembler accessProfiles,
                               Clock clock) {
        this.tokens = tokens;
        this.jwt = jwt;
        this.revocation = revocation;
        this.cookies = cookies;
        this.accounts = accounts;
        this.accessProfiles = accessProfiles;
        this.clock = clock;
    }

    /**
     * Issues a support access token, writes the browser cookie, and returns the token so Bearer clients
     * (scenarios / mobile) can use the same act-as JWT.
     */
    IssuedAccessToken issueFor(SupportGrantView grant, AccountId agentId, HttpServletRequest request,
                               HttpServletResponse response) {
        revokeCurrentSupportCookie(request);
        List<String> authorities = buildAuthorities(grant);
        IssuedAccessToken issued = tokens.issueSupport(
            new AccountId(grant.customerId()),
            grant.customerEmail(),
            agentId,
            new SupportGrantId(grant.id()),
            authorities,
            new SessionId(grant.id()),
            grant.expiresAt());
        Duration maxAge = Duration.between(clock.instant(), issued.expiresAt());
        if (maxAge.isNegative() || maxAge.isZero()) {
            cookies.clearSupportAccess(response);
            return issued;
        }
        cookies.writeSupportAccess(response, issued.token(), maxAge);
        return issued;
    }

    /**
     * {@code TYPE_CUSTOMER} + grant scopes, plus customer authority snapshots when consented:
     * <ul>
     *   <li>{@code support:full} — entire customer profile (roles, permissions, membership);
     *       password change is allowed on the account endpoint when this scope is present;</li>
     *   <li>{@code support:content:read} only — membership + premium content authorities.</li>
     * </ul>
     * Grant approve/deny still requires the customer's own token ({@code refuseIfSupportSession}).
     */
    private List<String> buildAuthorities(SupportGrantView grant) {
        Set<String> authorities = new LinkedHashSet<>();
        authorities.add(AuthorityNames.typeAuthority(tr.gov.kurum.common.domain.AccountType.CUSTOMER));
        authorities.addAll(grant.scopes());
        boolean full = grant.scopes().contains(PermissionCodes.SUPPORT_FULL);
        boolean content = grant.scopes().contains(PermissionCodes.SUPPORT_CONTENT_READ);
        if (full || content) {
            accounts.findById(new AccountId(grant.customerId())).ifPresent(customer -> {
                for (String authority : AuthorityNames.of(accessProfiles.profileOf(customer))) {
                    if (full
                            || authority.startsWith(AuthorityNames.MEMBERSHIP_PREFIX)
                            || PermissionCodes.CONTENT_PREMIUM_READ.equals(authority)) {
                        authorities.add(authority);
                    }
                }
            });
        }
        return new ArrayList<>(authorities);
    }

    void clear(HttpServletRequest request, HttpServletResponse response) {
        revokeCurrentSupportCookie(request);
        cookies.clearSupportAccess(response);
    }

    SupportSessionView currentSession(HttpServletRequest request) {
        String token = cookies.readSupportAccessToken(request);
        if (token == null) {
            return inactive();
        }
        VerifiedAccessToken verified;
        try {
            verified = jwt.verify(token);
        } catch (RuntimeException e) {
            return inactive();
        }
        if (!verified.isSupportSession()) {
            return inactive();
        }
        if (revocation.isRevoked(verified.tokenId(), verified.account(), verified.issuedAt())) {
            return inactive();
        }
        List<String> scopes = verified.authorities().stream()
            .filter(a -> a.startsWith("support:"))
            .toList();
        return new SupportSessionView(true, verified.grantId(), verified.account().value(), verified.email(),
            verified.expiresAt(), scopes);
    }

    private void revokeCurrentSupportCookie(HttpServletRequest request) {
        String token = cookies.readSupportAccessToken(request);
        if (token == null) {
            return;
        }
        jwt.verifyLeniently(token).ifPresent(verified ->
            revocation.revokeToken(verified.tokenId(), verified.expiresAt()));
    }

    private static SupportSessionView inactive() {
        return new SupportSessionView(false, null, 0L, "", null, List.of());
    }
}
