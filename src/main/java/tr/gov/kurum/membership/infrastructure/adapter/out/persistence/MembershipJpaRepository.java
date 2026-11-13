package tr.gov.kurum.membership.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface MembershipJpaRepository extends JpaRepository<MembershipJpaEntity, Long> {
}
