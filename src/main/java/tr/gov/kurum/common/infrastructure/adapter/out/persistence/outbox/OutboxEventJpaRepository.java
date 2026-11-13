package tr.gov.kurum.common.infrastructure.adapter.out.persistence.outbox;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, Long> {

    @Query(value = "SELECT nextval('outbox_event_id_seq')", nativeQuery = true)
    long nextId();

    /**
     * Locks the oldest due event. {@code SKIP LOCKED}: several instances relay in parallel without waiting
     * for each other and without delivering the same event twice at the same time.
     */
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE published_at IS NULL AND next_attempt_at <= :now AND attempts < :maxAttempts
            ORDER BY id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<OutboxEventJpaEntity> lockNextDue(@Param("now") Instant now, @Param("maxAttempts") int maxAttempts);

    @Modifying
    @Query(value = """
            DELETE FROM outbox_events
            WHERE id IN (SELECT id FROM outbox_events WHERE published_at < :before
                         ORDER BY id LIMIT :batchSize FOR UPDATE SKIP LOCKED)
            """, nativeQuery = true)
    int deletePublishedBefore(@Param("before") Instant before, @Param("batchSize") int batchSize);
}
