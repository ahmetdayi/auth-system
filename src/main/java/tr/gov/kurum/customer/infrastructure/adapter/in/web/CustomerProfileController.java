package tr.gov.kurum.customer.infrastructure.adapter.in.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.PermissionCodes;
import tr.gov.kurum.common.web.AuthenticatedAccount;
import tr.gov.kurum.common.web.CurrentAccount;
import tr.gov.kurum.customer.application.port.in.AddCustomerAddressCommand;
import tr.gov.kurum.customer.application.port.in.CustomerProfileUseCase;
import tr.gov.kurum.customer.application.port.in.CustomerProfileView;
import tr.gov.kurum.customer.application.port.in.RenameCustomerCommand;
import tr.gov.kurum.customer.domain.model.AddressId;
import tr.gov.kurum.customer.infrastructure.adapter.in.web.CustomerDtos.AddAddressRequest;
import tr.gov.kurum.customer.infrastructure.adapter.in.web.CustomerDtos.CreatedResponse;
import tr.gov.kurum.customer.infrastructure.adapter.in.web.CustomerDtos.RenameRequest;

/**
 * A customer's own profile. The token only says who the caller is; the profile is read from this context's
 * own tables. Password and sessions are the identity context's ({@code /api/account/**}), membership is the
 * membership context's ({@code /api/membership}).
 */
@RestController
@RequestMapping("/api")
class CustomerProfileController {

    /** The customer's own permission, or the scope an agent got through a support grant. */
    private static final String WRITE_PROFILE = "hasAuthority('TYPE_CUSTOMER') and (hasAuthority('"
        + PermissionCodes.PROFILE_WRITE + "') or hasAuthority('" + PermissionCodes.SUPPORT_PROFILE_WRITE + "'))";

    private final CustomerProfileUseCase profiles;

    CustomerProfileController(CustomerProfileUseCase profiles) {
        this.profiles = profiles;
    }

    /** An admin calling this gets 403: an admin account has no customer profile. */
    @GetMapping("/customer/profile")
    @PreAuthorize("hasAuthority('TYPE_CUSTOMER') and (hasAuthority('" + PermissionCodes.PROFILE_READ + "')"
        + " or hasAuthority('" + PermissionCodes.SUPPORT_PROFILE_READ + "'))")
    CustomerProfileView profile(@CurrentAccount AuthenticatedAccount account) {
        return profiles.getProfile(account.requireCustomer());
    }

    @PostMapping("/customer/addresses")
    @PreAuthorize(WRITE_PROFILE)
    ResponseEntity<CreatedResponse> addAddress(@CurrentAccount AuthenticatedAccount account,
                                               @RequestBody @Valid AddAddressRequest body) {
        AddressId id = profiles.addAddress(
            new AddCustomerAddressCommand(account.requireCustomer(), body.label(), body.city(), body.line()));
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreatedResponse(id.value()));
    }

    @DeleteMapping("/customer/addresses/{addressId}")
    @PreAuthorize(WRITE_PROFILE)
    ResponseEntity<Void> removeAddress(@CurrentAccount AuthenticatedAccount account, @PathVariable long addressId) {
        profiles.removeAddress(account.requireCustomer(), new AddressId(addressId));
        return ResponseEntity.noContent().build();
    }

    /**
     * Resource-based rule: the customer themself, or an admin with {@code customer:write}. A customer sending
     * someone else's id gets 403; an id that is not a customer's gets 404 (no customer profile).
     */
    @PutMapping("/customers/{id}/name")
    @PreAuthorize("@customerAccess.canModify(authentication, #id)")
    ResponseEntity<Void> rename(@PathVariable long id, @RequestBody @Valid RenameRequest body) {
        profiles.rename(new RenameCustomerCommand(new AccountId(id), body.firstName(), body.lastName()));
        return ResponseEntity.noContent().build();
    }
}
