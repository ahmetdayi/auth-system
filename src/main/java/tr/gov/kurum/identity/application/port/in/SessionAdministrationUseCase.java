package tr.gov.kurum.identity.application.port.in;

import java.util.List;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.AccountType;
import tr.gov.kurum.identity.domain.model.SessionId;

/**
 * Admin operations on the sessions of an account. Authorization is enforced by the web adapter; the expected
 * account type is checked here, so that a URL for customer sessions can never reach an admin's sessions
 * (all accounts share one id space).
 */
public interface SessionAdministrationUseCase {

    /** @throws tr.gov.kurum.common.domain.EntityNotFoundException if there is no such account of that type */
    List<SessionView> listActiveSessionsOf(AccountId account, AccountType expectedType);

    /** Ends every session and revokes every access token of the account immediately. */
    void terminateAllSessions(AccountId account, AccountType expectedType);

    /** Ends one session of {@code owner}; a session of another account is reported as not found. */
    void revokeSession(AccountId owner, AccountType expectedType, SessionId sessionId);
}
