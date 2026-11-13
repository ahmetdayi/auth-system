package tr.gov.kurum.customer.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tr.gov.kurum.common.infrastructure.adapter.out.persistence.BaseJpaEntity;

/** Child of {@link CustomerProfileJpaEntity}; has no repository of its own. */
@Entity
@Table(name = "customer_addresses")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class CustomerAddressJpaEntity extends BaseJpaEntity {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private CustomerProfileJpaEntity profile;

    @Column(name = "label", nullable = false, length = 40)
    private String label;

    @Column(name = "city", nullable = false, length = 80)
    private String city;

    @Column(name = "line", nullable = false, length = 200)
    private String line;

    CustomerAddressJpaEntity(Long id, CustomerProfileJpaEntity profile) {
        this.id = id;
        this.profile = profile;
    }
}
