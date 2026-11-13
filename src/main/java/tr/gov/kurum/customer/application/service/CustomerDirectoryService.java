package tr.gov.kurum.customer.application.service;

import org.springframework.stereotype.Service;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.query.PageResult;
import tr.gov.kurum.customer.application.port.in.CustomerDirectoryEntry;
import tr.gov.kurum.customer.application.port.in.CustomerDirectoryUseCase;
import tr.gov.kurum.customer.application.port.in.ListCustomersQuery;
import tr.gov.kurum.customer.application.port.out.CustomerProfileRepository;

@Service
public class CustomerDirectoryService implements CustomerDirectoryUseCase {

    private final CustomerProfileRepository repository;

    CustomerDirectoryService(CustomerProfileRepository repository) {
        this.repository = repository;
    }

    @Override
    public PageResult<CustomerDirectoryEntry> listCustomers(ListCustomersQuery query) {
        return repository.findDirectory(query);
    }

    @Override
    public boolean exists(AccountId customer) {
        return repository.existsById(customer);
    }
}
