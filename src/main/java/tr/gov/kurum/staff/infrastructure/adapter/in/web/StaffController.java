package tr.gov.kurum.staff.infrastructure.adapter.in.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tr.gov.kurum.common.web.AuthenticatedAccount;
import tr.gov.kurum.common.web.CurrentAccount;
import tr.gov.kurum.staff.application.port.in.AdminProfileUseCase;
import tr.gov.kurum.staff.application.port.in.AdminProfileView;

/**
 * The admin's own profile. {@code hasRole('ADMIN')} also admits SUPER_ADMIN through the role hierarchy; the
 * URL rule for {@code /api/admin/**} additionally requires TYPE_ADMIN.
 */
@RestController
@RequestMapping("/api/admin")
class StaffController {

    private final AdminProfileUseCase profiles;

    StaffController(AdminProfileUseCase profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    AdminProfileView dashboard(@CurrentAccount AuthenticatedAccount account) {
        return profiles.getProfile(account.requireAdmin());
    }
}
