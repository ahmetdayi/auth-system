package tr.gov.kurum.identity.application.service;

import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tr.gov.kurum.common.domain.BusinessRuleException;
import tr.gov.kurum.identity.application.port.in.ChangePasswordCommand;
import tr.gov.kurum.identity.application.port.in.PasswordChangeUseCase;
import tr.gov.kurum.identity.application.port.out.LoginAttemptLimiter;
import tr.gov.kurum.identity.application.port.out.PasswordHasher;
import tr.gov.kurum.identity.application.port.out.UserAccountRepository;
import tr.gov.kurum.identity.domain.model.IdentityErrorKey;
import tr.gov.kurum.identity.domain.model.PlainTextPassword;
import tr.gov.kurum.identity.domain.model.UserAccount;

@Service
public class PasswordChangeService implements PasswordChangeUseCase {

    private final UserAccountRepository accountRepository;
    private final AccountLoader accounts;
    private final PasswordHasher passwordHasher;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final TransactionTemplate transactionTemplate;
    private final DomainEventDispatcher eventDispatcher;
    private final Clock clock;

    PasswordChangeService(UserAccountRepository accountRepository, AccountLoader accounts,
                          PasswordHasher passwordHasher, LoginAttemptLimiter loginAttemptLimiter,
                          TransactionTemplate transactionTemplate, DomainEventDispatcher eventDispatcher,
                          Clock clock) {
        this.accountRepository = accountRepository;
        this.accounts = accounts;
        this.passwordHasher = passwordHasher;
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.transactionTemplate = transactionTemplate;
        this.eventDispatcher = eventDispatcher;
        this.clock = clock;
    }

    @Override
    public void changePassword(ChangePasswordCommand command) {
        PlainTextPassword currentPassword = PlainTextPassword.forVerification(command.currentPassword());
        PlainTextPassword newPassword = PlainTextPassword.forNewPassword(command.newPassword());
        UserAccount account = accounts.load(command.accountId());

        // A stolen access token must not allow brute-forcing the current password.
        if (loginAttemptLimiter.isBlocked(account.email(), command.clientIp())) {
            throw new BusinessRuleException(IdentityErrorKey.TOO_MANY_LOGIN_ATTEMPTS);
        }
        if (!passwordHasher.verify(currentPassword, account.passwordHash())) {
            loginAttemptLimiter.recordFailure(account.email(), command.clientIp());
            throw new BusinessRuleException(IdentityErrorKey.WRONG_CURRENT_PASSWORD);
        }

        account.changePassword(passwordHasher.hash(newPassword), clock.instant());   // hashing outside the tx
        // The save rejects the change if the account was modified since it was loaded above.
        transactionTemplate.executeWithoutResult(status -> {
            accountRepository.save(account);
            eventDispatcher.dispatch(account.pullDomainEvents());
        });
    }
}
