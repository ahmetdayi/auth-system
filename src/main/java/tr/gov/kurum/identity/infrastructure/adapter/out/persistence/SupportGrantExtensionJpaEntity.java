package tr.gov.kurum.identity.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tr.gov.kurum.common.infrastructure.adapter.out.persistence.BaseJpaEntity;
import tr.gov.kurum.identity.domain.model.SupportExtensionDecision;

/** Child of {@link SupportGrantJpaEntity}; has no repository of its own. */
@Entity
@Table(name = "support_grant_extensions")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class SupportGrantExtensionJpaEntity extends BaseJpaEntity {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grant_id", nullable = false, updatable = false)
    private SupportGrantJpaEntity grant;

    @Column(name = "requested_minutes", nullable = false, updatable = false)
    private int requestedMinutes;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 20)
    private SupportExtensionDecision decision;

    @Column(name = "decided_at")
    private Instant decidedAt;

    SupportGrantExtensionJpaEntity(Long id, SupportGrantJpaEntity grant) {
        this.id = id;
        this.grant = grant;
    }
}
