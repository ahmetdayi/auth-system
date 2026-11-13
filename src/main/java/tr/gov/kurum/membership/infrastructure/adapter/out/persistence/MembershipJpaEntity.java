package tr.gov.kurum.membership.infrastructure.adapter.out.persistence;

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
import tr.gov.kurum.membership.domain.model.MembershipTier;

@Entity
@Table(name = "memberships")
@PersistedAggregateRoot
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class MembershipJpaEntity extends BaseJpaEntity {

    /** The customer's account id; a scalar, never a relation to another context's entity (rule 5.2). */
    @Id
    @Column(name = "account_id")
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false, length = 20)
    private MembershipTier tier;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    MembershipJpaEntity(Long accountId) {
        this.accountId = accountId;
    }
}
