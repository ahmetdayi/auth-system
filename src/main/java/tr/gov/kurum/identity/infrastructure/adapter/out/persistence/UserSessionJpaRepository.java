package tr.gov.kurum.identity.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserSessionJpaRepository extends JpaRepository<UserSessionJpaEntity, Long> {

    @Query(value = "SELECT nextval('user_session_id_seq')", nativeQuery = true)
    long nextId();

    Optional<UserSessionJpaEntity> findByCurrentTokenHash(String currentTokenHash);

    List<UserSessionJpaEntity> findByAccountIdAndEndedAtIsNullAndExpiresAtAfterOrderByLastRefreshedAtDesc(
        Long accountId, Instant now, Limit limit);

    /**
     * Deletes one bounded batch; retired tokens go with it (ON DELETE CASCADE). {@code SKIP LOCKED} lets two
     * instances run the job at the same time without waiting on each other or deleting the same rows.
     */
    @Modifying
    @Query(value = """
            DELETE FROM user_sessions
            WHERE id IN (SELECT id FROM user_sessions WHERE expires_at < :now
                         ORDER BY id LIMIT :batchSize FOR UPDATE SKIP LOCKED)
            """, nativeQuery = true)
    int deleteExpiredBatch(@Param("now") Instant now, @Param("batchSize") int batchSize);
}
