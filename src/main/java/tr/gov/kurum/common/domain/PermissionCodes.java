package tr.gov.kurum.common.domain;

/**
 * Compile-time permission authority strings for {@code @PreAuthorize} and assignments. Keep in sync with
 * identity's {@code KnownPermission} catalog (each code appears there with description and group).
 */
public final class PermissionCodes {

    public static final String PROFILE_READ = "profile:read";
    public static final String PROFILE_WRITE = "profile:write";
    public static final String CUSTOMER_READ = "customer:read";
    public static final String CUSTOMER_WRITE = "customer:write";
    public static final String CONTENT_PREMIUM_READ = "content:premium:read";
    public static final String SUPPORT_PROFILE_READ = "support:profile:read";
    public static final String SUPPORT_PROFILE_WRITE = "support:profile:write";
    /** Act-as: read content with the customer's membership / premium permissions (snapshot at claim). */
    public static final String SUPPORT_CONTENT_READ = "support:content:read";
    /**
     * Act-as: snapshot of the customer's full authority set (roles, permissions, membership).
     * Password change and customer-side grant decisions stay blocked.
     */
    public static final String SUPPORT_FULL = "support:full";

    private PermissionCodes() {
    }
}
