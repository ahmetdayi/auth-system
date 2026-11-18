package tr.gov.kurum.identity.infrastructure.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import tr.gov.kurum.identity.domain.model.SupportGrantStatus;

interface SupportGrantJpaRepository extends JpaRepository<SupportGrantJpaEntity, Long> {

    @Query(value = "SELECT nextval('support_grant_id_seq')", nativeQuery = true)
    long nextId();

    @Query(value = "SELECT nextval('support_grant_extension_id_seq')", nativeQuery = true)
    long nextExtensionId();

    boolean existsByCustomerIdAndStatusIn(Long customerId, Collection<SupportGrantStatus> statuses);

    List<SupportGrantJpaEntity> findByAgentIdOrderByRequestedAtDesc(Long agentId, Limit limit);

    List<SupportGrantJpaEntity> findByCustomerIdOrderByRequestedAtDesc(Long customerId, Limit limit);
}
