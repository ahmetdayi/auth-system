package tr.gov.kurum.customer.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.AggregateRoot;
import tr.gov.kurum.common.domain.BusinessRuleException;
import tr.gov.kurum.common.domain.EmailAddress;
import tr.gov.kurum.common.domain.EntityNotFoundException;

/**
 * The customer as the business sees them: name, contact address, delivery addresses, loyalty points. Login,
 * password and status are the identity context's; membership is the membership context's. Identified by the
 * customer's {@link AccountId}.
 */
public final class CustomerProfile extends AggregateRoot {

    public static final int MAX_ADDRESSES = 5;

    private final AccountId id;
    private final EmailAddress contactEmail;
    private final int loyaltyPoints;
    private final List<Address> addresses;
    private final Instant registeredAt;
    private PersonName name;

    private CustomerProfile(CustomerProfileSnapshot state) {
        super(state.version());
        this.id = Objects.requireNonNull(state.id(), "id");
        this.contactEmail = Objects.requireNonNull(state.contactEmail(), "contactEmail");
        this.name = Objects.requireNonNull(state.name(), "name");
        if (state.loyaltyPoints() < 0) {
            throw new IllegalArgumentException("loyalty points cannot be negative");
        }
        this.loyaltyPoints = state.loyaltyPoints();
        this.addresses = new ArrayList<>(state.addresses());
        this.registeredAt = Objects.requireNonNull(state.registeredAt(), "registeredAt");
    }

    /** A new customer, created when the identity context reports that the account was opened. */
    public static CustomerProfile open(AccountId id, EmailAddress contactEmail, PersonName name,
                                       Instant registeredAt) {
        return new CustomerProfile(new CustomerProfileSnapshot(id, UNSAVED_VERSION, contactEmail, name, 0,
            List.of(), registeredAt));
    }

    public static CustomerProfile restore(CustomerProfileSnapshot snapshot) {
        return new CustomerProfile(snapshot);
    }

    // --- behaviour -------------------------------------------------------------------------------

    public void rename(PersonName newName) {
        this.name = Objects.requireNonNull(newName, "newName");
    }

    public void addAddress(AddressId addressId, String label, String city, String line) {
        if (addresses.size() >= MAX_ADDRESSES) {
            throw new BusinessRuleException(CustomerErrorKey.ADDRESS_LIMIT_REACHED, MAX_ADDRESSES);
        }
        addresses.add(Address.create(addressId, label, city, line));
    }

    public void removeAddress(AddressId addressId) {
        if (!addresses.removeIf(address -> address.id().equals(addressId))) {
            throw new EntityNotFoundException(CustomerErrorKey.ADDRESS_NOT_FOUND, addressId.value());
        }
    }

    // --- accessors (no setters) ---------------------------------------------------------------------

    public AccountId id() {
        return id;
    }

    public EmailAddress contactEmail() {
        return contactEmail;
    }

    public PersonName name() {
        return name;
    }

    public int loyaltyPoints() {
        return loyaltyPoints;
    }

    public List<Address> addresses() {
        return List.copyOf(addresses);
    }

    public Instant registeredAt() {
        return registeredAt;
    }
}
