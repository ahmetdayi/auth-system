package tr.gov.kurum.identity.domain.model;

/** Device information shown in the session list. Values are truncated to their column sizes. */
public record ClientDetails(String userAgent, String ipAddress) {

    private static final String UNKNOWN = "unknown";
    private static final int MAX_USER_AGENT = 255;
    private static final int MAX_IP = 45;

    public ClientDetails {
        userAgent = truncate(userAgent, MAX_USER_AGENT);
        ipAddress = truncate(ipAddress, MAX_IP);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
