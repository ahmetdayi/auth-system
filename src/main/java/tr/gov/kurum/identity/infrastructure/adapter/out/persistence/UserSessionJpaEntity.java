package tr.gov.kurum.identity.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tr.gov.kurum.common.infrastructure.adapter.out.persistence.BaseJpaEntity;
import tr.gov.kurum.common.infrastructure.adapter.out.persistence.PersistedAggregateRoot;
import tr.gov.kurum.identity.domain.model.SessionEndReason;

@Entity
@Table(name = "user_sessions")
@PersistedAggregateRoot
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class UserSessionJpaEntity extends BaseJpaEntity {

    @Id
    private Long id;

    /** Same aggregate boundary rule as everywhere: the account is referenced by id, not by a relation. */
    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;

    @Column(name = "user_agent", nullable = false, length = 255)
    private String userAgent;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "last_refreshed_at", nullable = false)
    private Instant lastRefreshedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "current_token_hash", nullable = false, length = 64)
    private String currentTokenHash;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 40)
    private SessionEndReason endReason;

    UserSessionJpaEntity(Long id) {
        this.id = id;
    }
}
