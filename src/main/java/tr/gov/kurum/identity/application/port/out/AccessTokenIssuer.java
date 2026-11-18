package tr.gov.kurum.identity.application.port.out;

import java.time.Instant;
import java.util.Collection;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.identity.domain.model.AccessProfile;
import tr.gov.kurum.identity.domain.model.SessionId;
import tr.gov.kurum.identity.domain.model.SupportGrantId;

public interface AccessTokenIssuer {

    /** @param sessionId the session the token belongs to, so a device can recognise "this session" */
    IssuedAccessToken issue(AccessProfile profile, SessionId sessionId);

    /**
     * Act-as token for a support grant: {@code sub} = customer, {@code actor} = agent, authorities =
     * grant scopes (+ TYPE_CUSTOMER). TTL is the earlier of the normal access TTL and {@code grantExpiresAt}.
     */
    IssuedAccessToken issueSupport(AccountId customerId, String customerEmail, AccountId actorId,
                                   SupportGrantId grantId, Collection<String> authorities,
                                   SessionId agentSessionId, Instant grantExpiresAt);
}
