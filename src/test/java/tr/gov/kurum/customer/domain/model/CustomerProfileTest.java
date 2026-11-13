package tr.gov.kurum.customer.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.BusinessRuleException;
import tr.gov.kurum.common.domain.EmailAddress;
import tr.gov.kurum.common.domain.EntityNotFoundException;

class CustomerProfileTest {

    private static final Instant T0 = Instant.parse("2026-03-01T10:00:00Z");

    private static CustomerProfile newProfile() {
        return CustomerProfile.open(new AccountId(10), EmailAddress.of("ayse@example.com"),
            new PersonName("Ayşe", "Yılmaz"), T0);
    }

    @Test
    void a_new_profile_starts_without_points_or_addresses() {
        CustomerProfile profile = newProfile();

        assertThat(profile.isNew()).isTrue();
        assertThat(profile.loyaltyPoints()).isZero();
        assertThat(profile.addresses()).isEmpty();
        assertThat(profile.registeredAt()).isEqualTo(T0);
    }

    @Test
    void names_are_trimmed_and_validated() {
        CustomerProfile profile = newProfile();

        profile.rename(new PersonName("  Ayşe Nur ", " Kaya "));

        assertThat(profile.name()).isEqualTo(new PersonName("Ayşe Nur", "Kaya"));
        assertThatThrownBy(() -> new PersonName(" ", "Kaya"))
            .isInstanceOf(BusinessRuleException.class)
            .extracting(e -> ((BusinessRuleException) e).errorKey())
            .isEqualTo(CustomerErrorKey.INVALID_PERSON_NAME);
        assertThatThrownBy(() -> new PersonName("A".repeat(101), "Kaya")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void address_limit_is_enforced_by_the_aggregate() {
        CustomerProfile profile = newProfile();
        for (int i = 1; i <= CustomerProfile.MAX_ADDRESSES; i++) {
            profile.addAddress(new AddressId(i), "Adres " + i, "Ankara", "Sokak " + i);
        }

        assertThatThrownBy(() -> profile.addAddress(new AddressId(99), "Fazla", "Ankara", "Sokak"))
            .isInstanceOf(BusinessRuleException.class)
            .extracting(e -> ((BusinessRuleException) e).errorKey())
            .isEqualTo(CustomerErrorKey.ADDRESS_LIMIT_REACHED);
    }

    @Test
    void removing_an_unknown_address_is_not_found() {
        assertThatThrownBy(() -> newProfile().removeAddress(new AddressId(5)))
            .isInstanceOf(EntityNotFoundException.class)
            .extracting(e -> ((EntityNotFoundException) e).errorKey())
            .isEqualTo(CustomerErrorKey.ADDRESS_NOT_FOUND);
    }

    @Test
    void addresses_are_returned_as_copies() {
        CustomerProfile profile = newProfile();
        profile.addAddress(new AddressId(1), "Ev", "İzmir", "Sokak 1");

        List<Address> addresses = profile.addresses();

        assertThatThrownBy(addresses::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThat(profile.addresses()).hasSize(1);
    }
}
