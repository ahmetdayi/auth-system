package tr.gov.kurum.customer.application.port.in;

import java.time.Instant;
import java.util.List;
import tr.gov.kurum.customer.domain.model.CustomerProfile;

/** What a customer sees on their profile page. Membership and login data come from their own contexts. */
public record CustomerProfileView(
        long id,
        String contactEmail,
        String firstName,
        String lastName,
        int loyaltyPoints,
        Instant registeredAt,
        List<AddressView> addresses) {

    public CustomerProfileView {
        addresses = List.copyOf(addresses);
    }

    public static CustomerProfileView of(CustomerProfile profile) {
        return new CustomerProfileView(
            profile.id().value(),
            profile.contactEmail().value(),
            profile.name().firstName(),
            profile.name().lastName(),
            profile.loyaltyPoints(),
            profile.registeredAt(),
            profile.addresses().stream().map(AddressView::of).toList());
    }
}
