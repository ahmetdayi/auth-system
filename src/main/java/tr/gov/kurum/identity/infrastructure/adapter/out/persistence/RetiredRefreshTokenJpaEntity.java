package tr.gov.kurum.identity.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tr.gov.kurum.common.infrastructure.adapter.out.persistence.BaseJpaEntity;

/**
 * Part of the session aggregate. Deliberately not mapped as a collection on {@link UserSessionJpaEntity}
 * (it can grow to hundreds of rows per session): the session adapter inserts and looks up rows directly.
 */
@Entity
@Table(name = "retired_refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class RetiredRefreshTokenJpaEntity extends BaseJpaEntity {

    @Id
    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @Column(name = "session_id", nullable = false, updatable = false)
    private Long sessionId;

    @Column(name = "retired_at", nullable = false, updatable = false)
    private Instant retiredAt;

    RetiredRefreshTokenJpaEntity(String tokenHash, Long sessionId, Instant retiredAt) {
        this.tokenHash = tokenHash;
        this.sessionId = sessionId;
        this.retiredAt = retiredAt;
    }
}
