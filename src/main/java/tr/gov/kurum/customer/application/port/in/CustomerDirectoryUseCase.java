package tr.gov.kurum.customer.application.port.in;

import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.query.PageResult;

/**
 * The customer list for back-office screens, and the published API other contexts use to ask whether a
 * customer exists (the membership context does, through its own outbound adapter).
 */
public interface CustomerDirectoryUseCase {

    PageResult<CustomerDirectoryEntry> listCustomers(ListCustomersQuery query);

    boolean exists(AccountId customer);
}
