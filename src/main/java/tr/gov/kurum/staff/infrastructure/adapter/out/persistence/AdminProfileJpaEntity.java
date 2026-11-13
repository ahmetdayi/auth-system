package tr.gov.kurum.staff.infrastructure.adapter.out.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tr.gov.kurum.common.infrastructure.adapter.out.persistence.BaseJpaEntity;
import tr.gov.kurum.common.infrastructure.adapter.out.persistence.PersistedAggregateRoot;

/** Read only for now: profiles are created by the migrations and the demo data. */
@Entity
@Table(name = "admin_profiles")
@PersistedAggregateRoot
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class AdminProfileJpaEntity extends BaseJpaEntity {

    /** The admin's account id; a scalar, never a relation to the identity context's entity (rule 5.2). */
    @Id
    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "department", length = 100)
    private String department;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "admin_profile_regions", joinColumns = @JoinColumn(name = "account_id"))
    @Column(name = "region", nullable = false, length = 100)
    private Set<String> managedRegions = new HashSet<>();
}
