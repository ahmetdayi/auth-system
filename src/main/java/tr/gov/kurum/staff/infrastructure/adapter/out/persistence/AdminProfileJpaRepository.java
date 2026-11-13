package tr.gov.kurum.staff.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface AdminProfileJpaRepository extends JpaRepository<AdminProfileJpaEntity, Long> {
}
