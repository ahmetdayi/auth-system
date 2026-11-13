package tr.gov.kurum.identity.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tr.gov.kurum.identity.application.port.out.RoleRepository;
import tr.gov.kurum.identity.domain.model.PermissionName;
import tr.gov.kurum.identity.domain.model.Role;
import tr.gov.kurum.identity.domain.model.RoleId;
import tr.gov.kurum.identity.domain.model.RoleName;

@Component
@RequiredArgsConstructor
class RolePersistenceAdapter implements RoleRepository {

    private final RoleJpaRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Optional<Role> findByName(RoleName name) {
        return repository.findByName(name.value()).map(RolePersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Role> findAllById(Set<RoleId> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return repository.findAllById(ids.stream().map(RoleId::value).toList()).stream()
            .map(RolePersistenceAdapter::toDomain)
            .toList();
    }

    private static Role toDomain(RoleJpaEntity entity) {
        return Role.restore(
            new RoleId(entity.getId()),
            entity.getVersion(),
            RoleName.of(entity.getName()),
            entity.getPermissions().stream().map(PermissionName::new).collect(Collectors.toSet()));
    }
}
