package tr.gov.kurum.customer.infrastructure.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tr.gov.kurum.customer.application.port.in.CustomerDirectoryEntry;

/** Package-private: nothing outside the persistence adapter sees Spring Data. */
interface CustomerProfileJpaRepository extends JpaRepository<CustomerProfileJpaEntity, Long> {

    @Query(value = "SELECT nextval('customer_address_id_seq')", nativeQuery = true)
    long nextAddressId();

    /** Projection straight into the read model: no aggregate, no lazy collections. */
    @Query(value = """
            SELECT new tr.gov.kurum.customer.application.port.in.CustomerDirectoryEntry(
                c.accountId, c.contactEmail, c.firstName, c.lastName, c.registeredAt)
            FROM CustomerProfileJpaEntity c
            WHERE c.contactEmail LIKE :emailPattern ESCAPE '\\'
            """,
        countQuery = """
            SELECT count(c) FROM CustomerProfileJpaEntity c WHERE c.contactEmail LIKE :emailPattern ESCAPE '\\'
            """)
    Page<CustomerDirectoryEntry> findDirectory(@Param("emailPattern") String emailPattern, Pageable pageable);
}
