package tr.gov.kurum.staff.domain.model;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.AggregateRoot;
import tr.gov.kurum.common.domain.BusinessRuleException;

/**
 * An administrator as the organisation sees them: name, department, the regions they look after. Login,
 * privilege level and permissions are the identity context's. Identified by the admin's {@link AccountId}.
 */
public final class AdminProfile extends AggregateRoot {

    private static final int MAX_FULL_NAME = 200;
    private static final int MAX_DEPARTMENT = 100;
    private static final int MAX_REGION = 100;

    private final AccountId id;
    private final String fullName;
    private final String department;
    private final Set<String> managedRegions;

    private AdminProfile(AccountId id, long version, String fullName, String department,
                         Set<String> managedRegions) {
        super(version);
        this.id = Objects.requireNonNull(id, "id");
        this.fullName = requireText(fullName, MAX_FULL_NAME);
        this.department = department == null || department.isBlank() ? null : requireText(department, MAX_DEPARTMENT);
        this.managedRegions = Set.copyOf(Objects.requireNonNull(managedRegions, "managedRegions"));
        this.managedRegions.forEach(region -> requireText(region, MAX_REGION));
    }

    public static AdminProfile restore(AccountId id, long version, String fullName, String department,
                                       Set<String> managedRegions) {
        return new AdminProfile(id, version, fullName, department, managedRegions);
    }

    public AccountId id() {
        return id;
    }

    public String fullName() {
        return fullName;
    }

    public Optional<String> department() {
        return Optional.ofNullable(department);
    }

    public Set<String> managedRegions() {
        return managedRegions;
    }

    private static String requireText(String value, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new BusinessRuleException(StaffErrorKey.INVALID_ADMIN_PROFILE);
        }
        return value.trim();
    }
}
