package tr.gov.kurum.identity.application.port.in;

import java.time.Instant;
import java.util.List;

/** What the caller's current act-as session looks like; {@code active == false} outside a support session. */
public record SupportSessionView(
        boolean active,
        Long grantId,
        long customerId,
        String customerEmail,
        Instant expiresAt,
        List<String> scopes) {
}
