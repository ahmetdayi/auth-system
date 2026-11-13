package tr.gov.kurum.customer.application.port.in;

import tr.gov.kurum.customer.domain.model.Address;

public record AddressView(long id, String label, String city, String line) {

    public static AddressView of(Address address) {
        return new AddressView(address.id().value(), address.label(), address.city(), address.line());
    }
}
