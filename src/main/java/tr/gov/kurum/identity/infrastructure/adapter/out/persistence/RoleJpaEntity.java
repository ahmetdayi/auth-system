package tr.gov.kurum.identity.infrastructure.adapter.out.persistence;

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

/** Reference data, maintained by migrations; read-only in the application. */
@Entity
@Table(name = "roles")
@PersistedAggregateRoot
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class RoleJpaEntity extends BaseJpaEntity {

    @Id
    private Long id;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission_name", nullable = false, length = 100)
    private Set<String> permissions = new HashSet<>();
}
