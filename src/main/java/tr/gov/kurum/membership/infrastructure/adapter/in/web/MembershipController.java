package tr.gov.kurum.membership.infrastructure.adapter.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.web.AuthenticatedAccount;
import tr.gov.kurum.common.web.CurrentAccount;
import tr.gov.kurum.membership.application.port.in.GrantMembershipCommand;
import tr.gov.kurum.membership.application.port.in.MembershipAdministrationUseCase;
import tr.gov.kurum.membership.application.port.in.MembershipQueryUseCase;
import tr.gov.kurum.membership.application.port.in.MembershipView;
import tr.gov.kurum.membership.domain.model.MembershipTier;

@RestController
@RequestMapping("/api")
class MembershipController {

    private final MembershipQueryUseCase queries;
    private final MembershipAdministrationUseCase administration;

    MembershipController(MembershipQueryUseCase queries, MembershipAdministrationUseCase administration) {
        this.queries = queries;
        this.administration = administration;
    }

    /** The caller's own membership; 204 when there is none. */
    @GetMapping("/membership")
    @PreAuthorize("hasAuthority('TYPE_CUSTOMER')")
    ResponseEntity<MembershipView> myMembership(@CurrentAccount AuthenticatedAccount account) {
        return queries.membershipOf(account.requireCustomer())
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** For admin screens that put data of several contexts side by side. */
    @GetMapping("/admin/memberships")
    @PreAuthorize("hasAuthority('TYPE_ADMIN') and hasAuthority('customer:read')")
    List<MembershipView> memberships(@RequestParam @Size(min = 1, max = MembershipQueryUseCase.MAX_LOOKUP_IDS)
                                     List<Long> ids) {
        return queries.membershipsOf(ids.stream().map(AccountId::new).collect(Collectors.toSet()));
    }

    @PutMapping("/admin/customers/{customerId}/membership")
    @PreAuthorize("hasAuthority('TYPE_ADMIN') and hasAuthority('customer:write')")
    ResponseEntity<Void> grant(@PathVariable long customerId, @RequestBody @Valid GrantMembershipRequest body) {
        administration.grant(new GrantMembershipCommand(new AccountId(customerId), body.tier(), body.validUntil()));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/admin/customers/{customerId}/membership")
    @PreAuthorize("hasAuthority('TYPE_ADMIN') and hasAuthority('customer:write')")
    ResponseEntity<Void> cancel(@PathVariable long customerId) {
        administration.cancel(new AccountId(customerId));
        return ResponseEntity.noContent().build();
    }

    /** @param validUntil exclusive end; {@code null} for a membership without end date */
    public record GrantMembershipRequest(@NotNull MembershipTier tier, Instant validUntil) {
    }
}
