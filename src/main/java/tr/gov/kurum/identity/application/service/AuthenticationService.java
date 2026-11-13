package tr.gov.kurum.identity.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.BusinessRuleException;
import tr.gov.kurum.common.domain.EmailAddress;
import tr.gov.kurum.common.domain.EntityNotFoundException;
import tr.gov.kurum.identity.application.port.in.AuthenticateCommand;
import tr.gov.kurum.identity.application.port.in.AuthenticationResult;
import tr.gov.kurum.identity.application.port.in.AuthenticationUseCase;
import tr.gov.kurum.identity.application.port.in.LogoutCommand;
import tr.gov.kurum.identity.application.port.in.RefreshSessionCommand;
import tr.gov.kurum.identity.application.port.out.AccessTokenIssuer;
import tr.gov.kurum.identity.application.port.out.AccessTokenRevocationStore;
import tr.gov.kurum.identity.application.port.out.LocatedSession;
import tr.gov.kurum.identity.application.port.out.LoginAttemptLimiter;
import tr.gov.kurum.identity.application.port.out.PasswordHasher;
import tr.gov.kurum.identity.application.port.out.RefreshTokenGenerator;
import tr.gov.kurum.identity.application.port.out.UserAccountRepository;
import tr.gov.kurum.identity.application.port.out.UserSessionRepository;
import tr.gov.kurum.identity.domain.model.AccessProfile;
import tr.gov.kurum.identity.domain.model.AccountStanding;
import tr.gov.kurum.identity.domain.model.ClientDetails;
import tr.gov.kurum.identity.domain.model.IdentityErrorKey;
import tr.gov.kurum.identity.domain.model.PlainTextPassword;
import tr.gov.kurum.identity.domain.model.RefreshOutcome;
import tr.gov.kurum.identity.domain.model.RefreshTokenHash;
import tr.gov.kurum.identity.domain.model.SessionId;
import tr.gov.kurum.identity.domain.model.SessionInvalidationCause;
import tr.gov.kurum.identity.domain.model.SessionPolicy;
import tr.gov.kurum.identity.domain.model.UserAccount;
import tr.gov.kurum.identity.domain.model.UserSession;

/**
 * Login, refresh, logout. Orchestration only: every decision is made by {@link UserAccount} and
 * {@link UserSession}. BCrypt runs outside transactions, so slow hashing never holds a database connection.
 */
@Service
public class AuthenticationService implements AuthenticationUseCase {

    private final UserAccountRepository accountRepository;
    private final UserSessionRepository sessionRepository;
    private final AccessProfileAssembler accessProfiles;
    private final PasswordHasher passwordHasher;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final AccessTokenRevocationStore revocationStore;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final SessionPolicy sessionPolicy;
    private final TransactionTemplate transactionTemplate;
    private final DomainEventDispatcher eventDispatcher;
    private final Clock clock;

    AuthenticationService(UserAccountRepository accountRepository, UserSessionRepository sessionRepository,
                          AccessProfileAssembler accessProfiles, PasswordHasher passwordHasher,
                          AccessTokenIssuer accessTokenIssuer, RefreshTokenGenerator refreshTokenGenerator,
                          AccessTokenRevocationStore revocationStore, LoginAttemptLimiter loginAttemptLimiter,
                          SessionPolicy sessionPolicy, TransactionTemplate transactionTemplate,
                          DomainEventDispatcher eventDispatcher, Clock clock) {
        this.accountRepository = accountRepository;
        this.sessionRepository = sessionRepository;
        this.accessProfiles = accessProfiles;
        this.passwordHasher = passwordHasher;
        this.accessTokenIssuer = accessTokenIssuer;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.revocationStore = revocationStore;
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.sessionPolicy = sessionPolicy;
        this.transactionTemplate = transactionTemplate;
        this.eventDispatcher = eventDispatcher;
        this.clock = clock;
    }

    @Override
    public AuthenticationResult authenticate(AuthenticateCommand command) {
        EmailAddress email = EmailAddress.of(command.email());
        PlainTextPassword password = PlainTextPassword.forVerification(command.password());
        String clientIp = command.client().ipAddress();

        if (loginAttemptLimiter.isBlocked(email, clientIp)) {
            throw new BusinessRuleException(IdentityErrorKey.TOO_MANY_LOGIN_ATTEMPTS);
        }

        Optional<UserAccount> account = accountRepository.findByEmail(email, command.accountType());

        // BCrypt runs whether or not the account exists, so response time does not reveal it.
        boolean passwordMatches = passwordHasher.verify(password,
            account.map(UserAccount::passwordHash).orElse(null));
        if (account.isEmpty() || !passwordMatches) {
            loginAttemptLimiter.recordFailure(email, clientIp);
            throw invalidCredentials();
        }
        UserAccount user = account.get();
        if (!user.standing().isActive()) {
            // Same answer as a wrong password: the account's state must not leak.
            throw invalidCredentials();
        }
        loginAttemptLimiter.recordSuccess(email, clientIp);

        AccessProfile profile = accessProfiles.profileOf(user);
        Instant now = clock.instant();
        String refreshToken = refreshTokenGenerator.generate();
        UserSession session = UserSession.start(sessionRepository.nextId(), user.id(), command.client(),
            RefreshTokenHash.of(refreshToken), sessionPolicy, now);
        transactionTemplate.executeWithoutResult(status -> sessionRepository.save(session));

        return new AuthenticationResult(profile, accessTokenIssuer.issue(profile, session.id()), refreshToken,
            session.id());
    }

    @Override
    public AuthenticationResult refresh(RefreshSessionCommand command) {
        RefreshTokenHash presentedHash = RefreshTokenHash.of(command.refreshToken());
        String newRefreshToken = refreshTokenGenerator.generate();
        RefreshTokenHash newHash = RefreshTokenHash.of(newRefreshToken);
        Instant now = clock.instant();

        RefreshAttempt attempt;
        try {
            // Committed even when the outcome is a rejection: ending a session on reuse must persist.
            attempt = transactionTemplate.execute(status -> rotate(presentedHash, newHash, command.client(), now));
        } catch (OptimisticLockingFailureException e) {
            // Another request rotated the same session concurrently.
            throw new BusinessRuleException(IdentityErrorKey.REFRESH_IN_PROGRESS, e);
        }
        attempt.outcome().requireRotated();

        // Roles, membership and status are read fresh: a change takes effect at the next refresh.
        AccessProfile profile = accessProfiles.profileOf(attempt.account());
        return new AuthenticationResult(profile, accessTokenIssuer.issue(profile, attempt.sessionId()),
            newRefreshToken, attempt.sessionId());
    }

    private RefreshAttempt rotate(RefreshTokenHash presentedHash, RefreshTokenHash newHash, ClientDetails client,
                                  Instant now) {
        LocatedSession located = sessionRepository.findByPresentedToken(presentedHash)
            .orElseThrow(() -> new BusinessRuleException(IdentityErrorKey.REFRESH_TOKEN_INVALID));
        UserSession session = located.session();
        Optional<UserAccount> account = accountRepository.findById(session.owner());
        AccountStanding standing = account.map(UserAccount::standing).orElseGet(AccountStanding::unavailable);

        RefreshOutcome outcome = session.refresh(located.presentedToken(), standing, newHash, client,
            sessionPolicy, now);
        sessionRepository.save(session);
        eventDispatcher.dispatch(session.pullDomainEvents());
        return new RefreshAttempt(outcome, account.orElse(null), session.id());
    }

    @Override
    public void logout(LogoutCommand command) {
        Instant now = clock.instant();
        if (command.accessToken() != null) {
            revocationStore.revokeToken(command.accessToken().tokenId(), command.accessToken().expiresAt());
        }
        RefreshTokenHash.tryOf(command.refreshToken()).ifPresent(hash ->
            transactionTemplate.executeWithoutResult(status ->
                sessionRepository.findByPresentedToken(hash).ifPresent(located -> {
                    located.session().logout(located.presentedToken(), now);
                    sessionRepository.save(located.session());
                })));
    }

    @Override
    public void logoutEverywhere(AccountId accountId) {
        Instant now = clock.instant();
        transactionTemplate.executeWithoutResult(status -> {
            UserAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException(IdentityErrorKey.ACCOUNT_NOT_FOUND, accountId.value()));
            account.invalidateAllSessions(SessionInvalidationCause.LOGOUT_EVERYWHERE, now);
            accountRepository.save(account);
            eventDispatcher.dispatch(account.pullDomainEvents());
        });
    }

    private static BusinessRuleException invalidCredentials() {
        return new BusinessRuleException(IdentityErrorKey.INVALID_CREDENTIALS);
    }

    /** {@code account} is {@code null} only when the outcome is a rejection. */
    private record RefreshAttempt(RefreshOutcome outcome, UserAccount account, SessionId sessionId) {
    }
}
