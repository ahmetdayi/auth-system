package tr.gov.kurum.customer.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.EmailAddress;
import tr.gov.kurum.common.domain.EntityNotFoundException;
import tr.gov.kurum.common.infrastructure.adapter.out.persistence.OptimisticVersions;
import tr.gov.kurum.common.query.PageResult;
import tr.gov.kurum.common.query.SortDirection;
import tr.gov.kurum.customer.application.port.in.CustomerDirectoryEntry;
import tr.gov.kurum.customer.application.port.in.CustomerSortField;
import tr.gov.kurum.customer.application.port.in.ListCustomersQuery;
import tr.gov.kurum.customer.application.port.out.CustomerProfileRepository;
import tr.gov.kurum.customer.domain.model.Address;
import tr.gov.kurum.customer.domain.model.AddressId;
import tr.gov.kurum.customer.domain.model.CustomerErrorKey;
import tr.gov.kurum.customer.domain.model.CustomerProfile;
import tr.gov.kurum.customer.domain.model.CustomerProfileSnapshot;
import tr.gov.kurum.customer.domain.model.PersonName;

@Component
class CustomerProfilePersistenceAdapter implements CustomerProfileRepository {

    private final CustomerProfileJpaRepository repository;

    CustomerProfilePersistenceAdapter(CustomerProfileJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public AddressId nextAddressId() {
        return new AddressId(repository.nextAddressId());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerProfile> findById(AccountId id) {
        return repository.findById(id.value()).map(CustomerProfilePersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(AccountId id) {
        return repository.existsById(id.value());
    }

    @Override
    @Transactional
    public void save(CustomerProfile profile) {
        if (profile.isNew()) {
            CustomerProfileJpaEntity entity = new CustomerProfileJpaEntity(profile.id().value());
            entity.setContactEmail(profile.contactEmail().value());
            entity.setRegisteredAt(profile.registeredAt());
            apply(profile, entity);
            repository.saveAndFlush(entity);
            return;
        }
        CustomerProfileJpaEntity entity = repository.findById(profile.id().value())
            .orElseThrow(() -> new EntityNotFoundException(CustomerErrorKey.CUSTOMER_NOT_FOUND, profile.id().value()));
        OptimisticVersions.requireSameVersion(entity, profile);
        apply(profile, entity);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CustomerDirectoryEntry> findDirectory(ListCustomersQuery query) {
        Sort.Direction direction = query.direction() == SortDirection.ASC ? Sort.Direction.ASC : Sort.Direction.DESC;
        // Secondary sort on the id keeps paging stable when the primary key has duplicates.
        Sort sort = Sort.by(direction, propertyOf(query.sortField())).and(Sort.by(Sort.Direction.ASC, "accountId"));
        PageRequest pageRequest = PageRequest.of(query.page().page(), query.page().size(), sort);

        Page<CustomerDirectoryEntry> page = repository.findDirectory(likePattern(query.emailContains()), pageRequest);
        return new PageResult<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }

    /** Whitelist mapping: the client chooses an enum value, never an entity path. */
    private static String propertyOf(CustomerSortField field) {
        return switch (field) {
            case EMAIL -> "contactEmail";
            case LAST_NAME -> "lastName";
            case REGISTERED_AT -> "registeredAt";
        };
    }

    /** E-mails are stored lower case; LIKE wildcards in user input are escaped. */
    private static String likePattern(String emailContains) {
        if (emailContains == null) {
            return "%";
        }
        String escaped = emailContains.toLowerCase(Locale.ROOT)
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static CustomerProfile toDomain(CustomerProfileJpaEntity entity) {
        List<Address> addresses = entity.getAddresses().stream()
            .map(a -> Address.restore(new AddressId(a.getId()), a.getLabel(), a.getCity(), a.getLine()))
            .toList();
        return CustomerProfile.restore(new CustomerProfileSnapshot(
            new AccountId(entity.getAccountId()),
            entity.getVersion(),
            EmailAddress.of(entity.getContactEmail()),
            new PersonName(entity.getFirstName(), entity.getLastName()),
            entity.getLoyaltyPoints(),
            addresses,
            entity.getRegisteredAt()));
    }

    /** Copies the mutable state of the aggregate onto a managed entity. */
    private static void apply(CustomerProfile profile, CustomerProfileJpaEntity entity) {
        entity.setFirstName(profile.name().firstName());
        entity.setLastName(profile.name().lastName());
        entity.setLoyaltyPoints(profile.loyaltyPoints());
        syncAddresses(profile, entity);
    }

    /** Addresses never change in place; they are only added or removed. */
    private static void syncAddresses(CustomerProfile profile, CustomerProfileJpaEntity entity) {
        Map<Long, Address> wanted = profile.addresses().stream()
            .collect(Collectors.toMap(a -> a.id().value(), Function.identity()));
        entity.getAddresses().removeIf(existing -> !wanted.containsKey(existing.getId()));
        Set<Long> present = entity.getAddresses().stream()
            .map(CustomerAddressJpaEntity::getId)
            .collect(Collectors.toSet());
        for (Address address : profile.addresses()) {
            if (!present.contains(address.id().value())) {
                CustomerAddressJpaEntity row = new CustomerAddressJpaEntity(address.id().value(), entity);
                row.setLabel(address.label());
                row.setCity(address.city());
                row.setLine(address.line());
                entity.getAddresses().add(row);
            }
        }
    }
}
