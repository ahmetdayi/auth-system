package tr.gov.kurum.identity.infrastructure.adapter.out.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface RoleJpaRepository extends JpaRepository<RoleJpaEntity, Long> {

    Optional<RoleJpaEntity> findByName(String name);
}
