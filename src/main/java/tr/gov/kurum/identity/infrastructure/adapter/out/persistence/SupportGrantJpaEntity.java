package tr.gov.kurum.identity.infrastructure.adapter.out.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import tr.gov.kurum.common.infrastructure.adapter.out.persistence.BaseJpaEntity;
import tr.gov.kurum.common.infrastructure.adapter.out.persistence.PersistedAggregateRoot;
import tr.gov.kurum.identity.domain.model.SupportEndReason;
import tr.gov.kurum.identity.domain.model.SupportGrantStatus;

@Entity
@Table(name = "support_grants")
@PersistedAggregateRoot
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class SupportGrantJpaEntity extends BaseJpaEntity {

    @Id
    private Long id;

    /** Both parties are accounts: scalar ids, no relation to the account entity. */
    @Column(name = "agent_id", nullable = false, updatable = false)
    private Long agentId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private SupportGrantStatus status;

    /** Comma-separated scope codes (permission codes never contain a comma). */
    @Column(name = "scopes", nullable = false, updatable = false)
    private String scopes;

    @Column(name = "initial_minutes", nullable = false, updatable = false)
    private int initialMinutes;

    @Column(name = "extension_count", nullable = false)
    private int extensionCount;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 32)
    private SupportEndReason endReason;

    /** Composition: extensions live and die with the grant and are kept for audit. */
    @OneToMany(mappedBy = "grant", fetch = FetchType.LAZY, orphanRemoval = true,
        cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE})
    @OrderBy("id")
    @BatchSize(size = 50)
    private List<SupportGrantExtensionJpaEntity> extensions = new ArrayList<>();

    SupportGrantJpaEntity(Long id) {
        this.id = id;
    }
}
