package tr.gov.kurum.customer.application.port.out;

import java.util.Optional;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.query.PageResult;
import tr.gov.kurum.customer.application.port.in.CustomerDirectoryEntry;
import tr.gov.kurum.customer.application.port.in.ListCustomersQuery;
import tr.gov.kurum.customer.domain.model.AddressId;
import tr.gov.kurum.customer.domain.model.CustomerProfile;

public interface CustomerProfileRepository {

    AddressId nextAddressId();

    Optional<CustomerProfile> findById(AccountId id);

    boolean existsById(AccountId id);

    /** Throws {@link org.springframework.dao.OptimisticLockingFailureException} for a stale profile. */
    void save(CustomerProfile profile);

    /** Read model straight from storage (rule 3.4); sort fields are a whitelist. */
    PageResult<CustomerDirectoryEntry> findDirectory(ListCustomersQuery query);
}
