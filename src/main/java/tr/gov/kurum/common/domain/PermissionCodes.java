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

    private PermissionCodes() {
    }
}
