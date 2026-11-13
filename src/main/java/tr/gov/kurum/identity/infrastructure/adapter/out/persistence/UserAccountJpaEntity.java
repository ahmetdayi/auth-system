package tr.gov.kurum.identity.infrastructure.adapter.out.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tr.gov.kurum.common.domain.AccountType;
import tr.gov.kurum.common.infrastructure.adapter.out.persistence.BaseJpaEntity;
import tr.gov.kurum.common.infrastructure.adapter.out.persistence.PersistedAggregateRoot;
import tr.gov.kurum.identity.domain.model.AccountStatus;
import tr.gov.kurum.identity.domain.model.AdminLevel;

@Entity
@Table(name = "accounts")
@PersistedAggregateRoot
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class UserAccountJpaEntity extends BaseJpaEntity {

    @Id
    private Long id;

    @Column(name = "email", nullable = false, updatable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, updatable = false, length = 20)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "admin_level", length = 20)
    private AdminLevel adminLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "sessions_valid_after", nullable = false)
    private Instant sessionsValidAfter;

    /** Roles are another aggregate: referenced by id only. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "account_roles", joinColumns = @JoinColumn(name = "account_id"))
    @Column(name = "role_id", nullable = false)
    private Set<Long> roleIds = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "account_permissions", joinColumns = @JoinColumn(name = "account_id"))
    @Column(name = "permission_name", nullable = false, length = 100)
    private Set<String> directPermissions = new HashSet<>();

    UserAccountJpaEntity(Long id) {
        this.id = id;
    }
}
