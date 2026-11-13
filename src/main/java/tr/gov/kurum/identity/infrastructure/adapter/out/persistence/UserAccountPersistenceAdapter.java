package tr.gov.kurum.identity.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.AccountType;
import tr.gov.kurum.common.domain.BusinessRuleException;
import tr.gov.kurum.common.domain.EmailAddress;
import tr.gov.kurum.common.domain.EntityNotFoundException;
import tr.gov.kurum.common.infrastructure.adapter.out.persistence.ConstraintViolations;
import tr.gov.kurum.common.infrastructure.adapter.out.persistence.OptimisticVersions;
import tr.gov.kurum.identity.application.port.in.AccountSummaryView;
import tr.gov.kurum.identity.application.port.out.UserAccountRepository;
import tr.gov.kurum.identity.domain.model.IdentityErrorKey;
import tr.gov.kurum.identity.domain.model.PasswordHash;
import tr.gov.kurum.identity.domain.model.PermissionName;
import tr.gov.kurum.identity.domain.model.RoleId;
import tr.gov.kurum.identity.domain.model.UserAccount;
import tr.gov.kurum.identity.domain.model.UserAccountSnapshot;

@Component
class UserAccountPersistenceAdapter implements UserAccountRepository {

    private static final String EMAIL_UNIQUE_CONSTRAINT = "uk_accounts_email_type";

    private final UserAccountJpaRepository repository;

    UserAccountPersistenceAdapter(UserAccountJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public AccountId nextId() {
        return new AccountId(repository.nextId());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAccount> findById(AccountId id) {
        return repository.findById(id.value()).map(UserAccountPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAccount> findByEmail(EmailAddress email, AccountType type) {
        return repository.findByEmailAndAccountType(email.value(), type).map(UserAccountPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountSummaryView> findSummaries(Set<AccountId> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return repository.findSummaries(ids.stream().map(AccountId::value).toList());
    }

    @Override
    @Transactional
    public void save(UserAccount account) {
        if (account.isNew()) {
            UserAccountJpaEntity entity = new UserAccountJpaEntity(account.id().value());
            entity.setEmail(account.email().value());
            entity.setAccountType(account.type());
            entity.setAdminLevel(account.adminLevel().orElse(null));
            apply(account, entity);
            insert(entity);
            return;
        }
        UserAccountJpaEntity entity = repository.findById(account.id().value())
            .orElseThrow(() -> new EntityNotFoundException(IdentityErrorKey.ACCOUNT_NOT_FOUND, account.id().value()));
        OptimisticVersions.requireSameVersion(entity, account);
        apply(account, entity);
    }

    private void insert(UserAccountJpaEntity entity) {
        try {
            repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            if (ConstraintViolations.isViolationOf(e, EMAIL_UNIQUE_CONSTRAINT)) {
                throw new BusinessRuleException(IdentityErrorKey.EMAIL_ALREADY_REGISTERED, e);
            }
            throw e;
        }
    }

    private static void apply(UserAccount account, UserAccountJpaEntity entity) {
        entity.setPasswordHash(account.passwordHash().value());
        entity.setStatus(account.status());
        entity.setSessionsValidAfter(account.sessionsValidAfter());
        // Collections are synchronised in place: Hibernate tracks the managed collection instances.
        Set<Long> roleIds = account.roleIds().stream().map(RoleId::value).collect(Collectors.toSet());
        entity.getRoleIds().retainAll(roleIds);
        entity.getRoleIds().addAll(roleIds);
        Set<String> permissions = account.directPermissions().stream().map(PermissionName::value)
            .collect(Collectors.toSet());
        entity.getDirectPermissions().retainAll(permissions);
        entity.getDirectPermissions().addAll(permissions);
    }

    private static UserAccount toDomain(UserAccountJpaEntity entity) {
        return UserAccount.restore(new UserAccountSnapshot(
            new AccountId(entity.getId()),
            entity.getVersion(),
            new EmailAddress(entity.getEmail()),
            new PasswordHash(entity.getPasswordHash()),
            entity.getAccountType(),
            entity.getAdminLevel(),
            entity.getStatus(),
            entity.getSessionsValidAfter(),
            entity.getRoleIds().stream().map(RoleId::new).collect(Collectors.toSet()),
            entity.getDirectPermissions().stream().map(PermissionName::new).collect(Collectors.toSet()),
            entity.getCreatedAt()));
    }
}
