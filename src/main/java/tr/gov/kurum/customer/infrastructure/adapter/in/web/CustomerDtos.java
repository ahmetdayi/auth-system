package tr.gov.kurum.customer.infrastructure.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request and response bodies of the customer endpoints. */
final class CustomerDtos {

    private CustomerDtos() {
    }

    record RenameRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName) {
    }

    record AddAddressRequest(
            @NotBlank @Size(max = 40) String label,
            @NotBlank @Size(max = 80) String city,
            @NotBlank @Size(max = 200) String line) {
    }

    record CreatedResponse(long id) {
    }
}
