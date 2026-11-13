package tr.gov.kurum.identity.application.service;

import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.EmailAddress;
import tr.gov.kurum.identity.application.port.in.CustomerRegistrationUseCase;
import tr.gov.kurum.identity.application.port.in.RegisterCustomerCommand;
import tr.gov.kurum.identity.application.port.out.PasswordHasher;
import tr.gov.kurum.identity.application.port.out.RoleRepository;
import tr.gov.kurum.identity.application.port.out.UserAccountRepository;
import tr.gov.kurum.identity.domain.model.PasswordHash;
import tr.gov.kurum.identity.domain.model.PlainTextPassword;
import tr.gov.kurum.identity.domain.model.Role;
import tr.gov.kurum.identity.domain.model.RoleName;
import tr.gov.kurum.identity.domain.model.UserAccount;

/**
 * Opens the account in one transaction. The customer profile is not written here (one aggregate per
 * transaction, rule 3.3): the account's {@code CustomerAccountOpened} event is translated into the published
 * {@code CustomerRegistered}, which goes into the outbox in the same transaction; the customer context creates
 * the profile when it is delivered.
 */
@Service
public class CustomerRegistrationService implements CustomerRegistrationUseCase {

    private static final RoleName DEFAULT_ROLE = RoleName.of("USER");

    private final UserAccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final PasswordHasher passwordHasher;
    private final TransactionTemplate transactionTemplate;
    private final DomainEventDispatcher eventDispatcher;
    private final Clock clock;

    CustomerRegistrationService(UserAccountRepository accountRepository, RoleRepository roleRepository,
                                PasswordHasher passwordHasher, TransactionTemplate transactionTemplate,
                                DomainEventDispatcher eventDispatcher, Clock clock) {
        this.accountRepository = accountRepository;
        this.roleRepository = roleRepository;
        this.passwordHasher = passwordHasher;
        this.transactionTemplate = transactionTemplate;
        this.eventDispatcher = eventDispatcher;
        this.clock = clock;
    }

    @Override
    public AccountId register(RegisterCustomerCommand command) {
        EmailAddress email = EmailAddress.of(command.email());
        PlainTextPassword password = PlainTextPassword.forNewPassword(command.password());
        Role defaultRole = roleRepository.findByName(DEFAULT_ROLE)
            .orElseThrow(() -> new IllegalStateException("Default role USER is missing; check the migrations"));

        PasswordHash passwordHash = passwordHasher.hash(password);   // outside the transaction (slow)
        UserAccount account = UserAccount.openCustomerAccount(accountRepository.nextId(), email, passwordHash,
            defaultRole.id(), command.firstName(), command.lastName(), clock.instant());
        transactionTemplate.executeWithoutResult(status -> {
            accountRepository.save(account);
            eventDispatcher.dispatch(account.pullDomainEvents());
        });
        return account.id();
    }
}
