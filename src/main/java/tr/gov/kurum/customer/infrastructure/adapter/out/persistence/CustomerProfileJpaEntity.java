package tr.gov.kurum.customer.infrastructure.adapter.out.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import tr.gov.kurum.common.infrastructure.adapter.out.persistence.BaseJpaEntity;
import tr.gov.kurum.common.infrastructure.adapter.out.persistence.PersistedAggregateRoot;

@Entity
@Table(name = "customer_profiles")
@PersistedAggregateRoot
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class CustomerProfileJpaEntity extends BaseJpaEntity {

    /** The customer's account id; a scalar, never a relation to the identity context's entity (rule 5.2). */
    @Id
    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "contact_email", nullable = false, length = 254, updatable = false)
    private String contactEmail;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "loyalty_points", nullable = false)
    private int loyaltyPoints;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt;

    /** Composition (max. 5 per customer): addresses live and die with the profile. */
    @OneToMany(mappedBy = "profile", fetch = FetchType.LAZY, orphanRemoval = true,
        cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE})
    @OrderBy("id")
    private List<CustomerAddressJpaEntity> addresses = new ArrayList<>();

    CustomerProfileJpaEntity(Long accountId) {
        this.accountId = accountId;
    }
}
