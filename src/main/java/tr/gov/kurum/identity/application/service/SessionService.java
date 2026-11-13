package tr.gov.kurum.identity.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.AccountType;
import tr.gov.kurum.common.domain.EntityNotFoundException;
import tr.gov.kurum.identity.application.port.in.SessionAdministrationUseCase;
import tr.gov.kurum.identity.application.port.in.SessionManagementUseCase;
import tr.gov.kurum.identity.application.port.in.SessionView;
import tr.gov.kurum.identity.application.port.out.UserAccountRepository;
import tr.gov.kurum.identity.application.port.out.UserSessionRepository;
import tr.gov.kurum.identity.domain.model.AccountStanding;
import tr.gov.kurum.identity.domain.model.IdentityErrorKey;
import tr.gov.kurum.identity.domain.model.SessionId;
import tr.gov.kurum.identity.domain.model.SessionInvalidationCause;
import tr.gov.kurum.identity.domain.model.UserAccount;
import tr.gov.kurum.identity.domain.model.UserSession;

@Service
public class SessionService implements SessionManagementUseCase, SessionAdministrationUseCase {

    /** A user rarely has more devices than this; the list is bounded instead of loading everything. */
    private static final int MAX_LISTED_SESSIONS = 50;

    private final UserSessionRepository sessionRepository;
    private final UserAccountRepository accountRepository;
    private final AccountLoader accounts;
    private final TransactionTemplate transactionTemplate;
    private final DomainEventDispatcher eventDispatcher;
    private final Clock clock;

    SessionService(UserSessionRepository sessionRepository, UserAccountRepository accountRepository,
                   AccountLoader accounts, TransactionTemplate transactionTemplate,
                   DomainEventDispatcher eventDispatcher, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.accountRepository = accountRepository;
        this.accounts = accounts;
        this.transactionTemplate = transactionTemplate;
        this.eventDispatcher = eventDispatcher;
        this.clock = clock;
    }

    @Override
    public List<SessionView> listActiveSessions(AccountId owner, SessionId currentSession) {
        AccountStanding standing = accountRepository.findById(owner).map(UserAccount::standing)
            .orElseGet(AccountStanding::unavailable);
        return activeSessions(owner, standing, currentSession);
    }

    @Override
    public void revokeOwnSession(AccountId owner, SessionId sessionId) {
        Instant now = clock.instant();
        transactionTemplate.executeWithoutResult(status -> {
            UserSession session = loadSession(sessionId);
            session.revokeByOwner(owner, now);
            sessionRepository.save(session);
        });
    }

    @Override
    public List<SessionView> listActiveSessionsOf(AccountId account, AccountType expectedType) {
        return activeSessions(account, accounts.load(account, expectedType).standing(), null);
    }

    @Override
    public void terminateAllSessions(AccountId accountId, AccountType expectedType) {
        Instant now = clock.instant();
        transactionTemplate.executeWithoutResult(status -> {
            UserAccount account = accounts.load(accountId, expectedType);
            account.invalidateAllSessions(SessionInvalidationCause.TERMINATED_BY_ADMIN, now);
            accountRepository.save(account);
            eventDispatcher.dispatch(account.pullDomainEvents());
        });
    }

    @Override
    public void revokeSession(AccountId owner, AccountType expectedType, SessionId sessionId) {
        accounts.load(owner, expectedType);
        Instant now = clock.instant();
        transactionTemplate.executeWithoutResult(status -> {
            UserSession session = loadSession(sessionId);
            session.revokeByAdmin(owner, now);
            sessionRepository.save(session);
        });
    }

    private List<SessionView> activeSessions(AccountId owner, AccountStanding standing, SessionId currentSession) {
        Instant now = clock.instant();
        return sessionRepository.findUnendedByOwner(owner, now, MAX_LISTED_SESSIONS).stream()
            .filter(session -> session.isActiveAt(now, standing))
            .map(session -> SessionView.of(session, currentSession))
            .toList();
    }

    private UserSession loadSession(SessionId sessionId) {
        return sessionRepository.findById(sessionId)
            .orElseThrow(() -> new EntityNotFoundException(IdentityErrorKey.SESSION_NOT_FOUND, sessionId.value()));
    }
}
