package tr.gov.kurum.identity.infrastructure.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tr.gov.kurum.common.domain.AccountType;
import tr.gov.kurum.identity.application.port.in.AccountSummaryView;

interface UserAccountJpaRepository extends JpaRepository<UserAccountJpaEntity, Long> {

    @Query(value = "SELECT nextval('account_id_seq')", nativeQuery = true)
    long nextId();

    Optional<UserAccountJpaEntity> findByEmailAndAccountType(String email, AccountType accountType);

    /** Projection straight into the read model: no aggregate, no lazy collections. */
    @Query("""
            SELECT new tr.gov.kurum.identity.application.port.in.AccountSummaryView(
                a.id, a.email, a.accountType, a.status, a.createdAt)
            FROM UserAccountJpaEntity a
            WHERE a.id IN :ids
            ORDER BY a.id
            """)
    List<AccountSummaryView> findSummaries(@Param("ids") Collection<Long> ids);
}
