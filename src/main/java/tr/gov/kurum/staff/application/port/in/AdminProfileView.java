package tr.gov.kurum.staff.application.port.in;

import java.util.List;
import tr.gov.kurum.staff.domain.model.AdminProfile;

/**
 * The staff context's part of the admin dashboard. E-mail, privilege level and permissions are the identity
 * context's and come from {@code /api/me}; the screen shows both side by side.
 *
 * @param department {@code null} when none is recorded
 * @param managedRegions sorted, for a stable display
 */
public record AdminProfileView(long id, String fullName, String department, List<String> managedRegions) {

    public AdminProfileView {
        managedRegions = List.copyOf(managedRegions);
    }

    public static AdminProfileView of(AdminProfile profile) {
        return new AdminProfileView(profile.id().value(), profile.fullName(), profile.department().orElse(null),
            profile.managedRegions().stream().sorted().toList());
    }
}
