package tr.gov.kurum.identity.infrastructure.adapter.out.persistence;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.BusinessRuleException;
import tr.gov.kurum.common.domain.EntityNotFoundException;
import tr.gov.kurum.common.infrastructure.adapter.out.persistence.ConstraintViolations;
import tr.gov.kurum.common.infrastructure.adapter.out.persistence.OptimisticVersions;
import tr.gov.kurum.identity.application.port.out.SupportGrantRepository;
import tr.gov.kurum.identity.domain.model.IdentityErrorKey;
import tr.gov.kurum.identity.domain.model.SupportGrant;
import tr.gov.kurum.identity.domain.model.SupportGrantExtension;
import tr.gov.kurum.identity.domain.model.SupportGrantExtensionId;
import tr.gov.kurum.identity.domain.model.SupportGrantId;
import tr.gov.kurum.identity.domain.model.SupportGrantStatus;

@Component
class SupportGrantPersistenceAdapter implements SupportGrantRepository {

    private static final String LIVE_GRANT_UNIQUE_CONSTRAINT = "uk_support_grants_customer_live";
    private static final String SCOPE_SEPARATOR = ",";
    /** A customer or agent rarely has more grants than this; lists are bounded instead of loading everything. */
    private static final Limit MAX_LISTED = Limit.of(100);
    private static final Set<SupportGrantStatus> LIVE_STATUSES = Set.of(
        SupportGrantStatus.PENDING, SupportGrantStatus.ACTIVE, SupportGrantStatus.PENDING_EXTENSION);

    private final SupportGrantJpaRepository repository;

    SupportGrantPersistenceAdapter(SupportGrantJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public SupportGrantId nextId() {
        return new SupportGrantId(repository.nextId());
    }

    @Override
    public SupportGrantExtensionId nextExtensionId() {
        return new SupportGrantExtensionId(repository.nextExtensionId());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SupportGrant> findById(SupportGrantId id) {
        return repository.findById(id.value()).map(SupportGrantPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional
    public void save(SupportGrant grant) {
        if (grant.isNew()) {
            SupportGrantJpaEntity entity = new SupportGrantJpaEntity(grant.id().value());
            entity.setAgentId(grant.agentId().value());
            entity.setCustomerId(grant.customerId().value());
            entity.setScopes(String.join(SCOPE_SEPARATOR, grant.scopes().stream().sorted().toList()));
            entity.setInitialMinutes(grant.initialMinutes());
            entity.setRequestedAt(grant.requestedAt());
            apply(grant, entity);
            insert(entity);
            return;
        }
        SupportGrantJpaEntity entity = repository.findById(grant.id().value())
            .orElseThrow(() -> new EntityNotFoundException(IdentityErrorKey.SUPPORT_GRANT_NOT_FOUND,
                grant.id().value()));
        OptimisticVersions.requireSameVersion(entity, grant);
        apply(grant, entity);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasLiveGrantForCustomer(AccountId customerId) {
        return repository.existsByCustomerIdAndStatusIn(customerId.value(), LIVE_STATUSES);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupportGrant> findByAgent(AccountId agentId) {
        return repository.findByAgentIdOrderByRequestedAtDesc(agentId.value(), MAX_LISTED).stream()
            .map(SupportGrantPersistenceAdapter::toDomain)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupportGrant> findByCustomer(AccountId customerId) {
        return repository.findByCustomerIdOrderByRequestedAtDesc(customerId.value(), MAX_LISTED).stream()
            .map(SupportGrantPersistenceAdapter::toDomain)
            .toList();
    }

    private void insert(SupportGrantJpaEntity entity) {
        try {
            repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            if (ConstraintViolations.isViolationOf(e, LIVE_GRANT_UNIQUE_CONSTRAINT)) {
                throw new BusinessRuleException(IdentityErrorKey.SUPPORT_GRANT_CONFLICT, e, entity.getCustomerId());
            }
            throw e;
        }
    }

    /** Copies the mutable state of the aggregate onto a managed entity. */
    private static void apply(SupportGrant grant, SupportGrantJpaEntity entity) {
        entity.setStatus(grant.status());
        entity.setExtensionCount(grant.extensionCount());
        entity.setExpiresAt(grant.expiresAt());
        entity.setStartedAt(grant.startedAt());
        entity.setEndedAt(grant.endedAt());
        entity.setEndReason(grant.endReason());
        syncExtensions(grant, entity);
    }

    /** Extensions are never removed (audit); new ones are added and decisions are updated in place. */
    private static void syncExtensions(SupportGrant grant, SupportGrantJpaEntity entity) {
        Map<Long, SupportGrantExtensionJpaEntity> stored = entity.getExtensions().stream()
            .collect(Collectors.toMap(SupportGrantExtensionJpaEntity::getId, Function.identity()));
        for (SupportGrantExtension extension : grant.extensions()) {
            SupportGrantExtensionJpaEntity row = stored.get(extension.id().value());
            if (row == null) {
                row = new SupportGrantExtensionJpaEntity(extension.id().value(), entity);
                row.setRequestedMinutes(extension.requestedMinutes());
                row.setRequestedAt(extension.requestedAt());
                entity.getExtensions().add(row);
            }
            row.setDecision(extension.decision());
            row.setDecidedAt(extension.decidedAt());
        }
    }

    private static SupportGrant toDomain(SupportGrantJpaEntity entity) {
        List<SupportGrantExtension> extensions = entity.getExtensions().stream()
            .map(e -> SupportGrantExtension.restore(new SupportGrantExtensionId(e.getId()), e.getRequestedMinutes(),
                e.getRequestedAt(), e.getDecision(), e.getDecidedAt()))
            .toList();
        Set<String> scopes = new LinkedHashSet<>(Arrays.asList(entity.getScopes().split(SCOPE_SEPARATOR)));
        return SupportGrant.restore(
            new SupportGrantId(entity.getId()),
            entity.getVersion(),
            new AccountId(entity.getAgentId()),
            new AccountId(entity.getCustomerId()),
            scopes,
            entity.getInitialMinutes(),
            entity.getStatus(),
            entity.getExtensionCount(),
            entity.getExpiresAt(),
            entity.getRequestedAt(),
            entity.getStartedAt(),
            entity.getEndedAt(),
            entity.getEndReason(),
            extensions);
    }
}
