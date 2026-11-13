package tr.gov.kurum.membership.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.EntityNotFoundException;
import tr.gov.kurum.common.infrastructure.adapter.out.persistence.OptimisticVersions;
import tr.gov.kurum.membership.application.port.out.MembershipRepository;
import tr.gov.kurum.membership.domain.model.Membership;
import tr.gov.kurum.membership.domain.model.MembershipErrorKey;
import tr.gov.kurum.membership.domain.model.ValidityPeriod;

@Component
class MembershipPersistenceAdapter implements MembershipRepository {

    private final MembershipJpaRepository repository;

    MembershipPersistenceAdapter(MembershipJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Membership> findById(AccountId memberId) {
        return repository.findById(memberId.value()).map(MembershipPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Membership> findAllById(Set<AccountId> memberIds) {
        if (memberIds.isEmpty()) {
            return List.of();
        }
        return repository.findAllById(memberIds.stream().map(AccountId::value).toList()).stream()
            .map(MembershipPersistenceAdapter::toDomain)
            .toList();
    }

    @Override
    @Transactional
    public void save(Membership membership) {
        MembershipJpaEntity entity;
        if (membership.isNew()) {
            entity = new MembershipJpaEntity(membership.memberId().value());
        } else {
            entity = load(membership);
            OptimisticVersions.requireSameVersion(entity, membership);
        }
        entity.setTier(membership.tier());
        entity.setStartsAt(membership.period().startInclusive());
        entity.setEndsAt(membership.period().end().orElse(null));
        if (membership.isNew()) {
            repository.save(entity);
        }
    }

    @Override
    @Transactional
    public void delete(Membership membership) {
        MembershipJpaEntity entity = load(membership);
        OptimisticVersions.requireSameVersion(entity, membership);
        repository.delete(entity);
    }

    private MembershipJpaEntity load(Membership membership) {
        return repository.findById(membership.memberId().value()).orElseThrow(() ->
            new EntityNotFoundException(MembershipErrorKey.MEMBERSHIP_NOT_FOUND, membership.memberId().value()));
    }

    private static Membership toDomain(MembershipJpaEntity entity) {
        return Membership.restore(new AccountId(entity.getAccountId()), entity.getVersion(), entity.getTier(),
            new ValidityPeriod(entity.getStartsAt(), entity.getEndsAt()));
    }
}
