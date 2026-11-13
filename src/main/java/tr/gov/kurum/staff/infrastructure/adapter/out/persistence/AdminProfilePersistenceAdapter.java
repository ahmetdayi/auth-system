package tr.gov.kurum.staff.infrastructure.adapter.out.persistence;

import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.staff.application.port.out.AdminProfileRepository;
import tr.gov.kurum.staff.domain.model.AdminProfile;

@Component
class AdminProfilePersistenceAdapter implements AdminProfileRepository {

    private final AdminProfileJpaRepository repository;

    AdminProfilePersistenceAdapter(AdminProfileJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AdminProfile> findById(AccountId id) {
        return repository.findById(id.value()).map(entity -> AdminProfile.restore(
            new AccountId(entity.getAccountId()),
            entity.getVersion(),
            entity.getFullName(),
            entity.getDepartment(),
            entity.getManagedRegions()));
    }
}
