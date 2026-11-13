package tr.gov.kurum.common.infrastructure.adapter.out.persistence;

import org.springframework.dao.OptimisticLockingFailureException;
import tr.gov.kurum.common.domain.AggregateRoot;

/**
 * Rejects saving an aggregate whose version differs from the stored one. Covers the case where the aggregate
 * was loaded in an earlier transaction (e.g. before a slow BCrypt check); concurrent transactions are
 * additionally caught by Hibernate's {@code @Version} check at flush.
 *
 * <p>Throws the same exception type as Hibernate's check, so callers handle one exception for both cases
 * (the API answers 409 CONCURRENT_UPDATE; the refresh flow turns it into REFRESH_IN_PROGRESS).</p>
 */
public final class OptimisticVersions {

    private OptimisticVersions() {
    }

    public static void requireSameVersion(BaseJpaEntity stored, AggregateRoot aggregate) {
        if (stored.getVersion() == null || stored.getVersion() != aggregate.version()) {
            throw new OptimisticLockingFailureException(
                "Stale aggregate: " + stored.getClass().getSimpleName() + " was modified concurrently");
        }
    }
}
