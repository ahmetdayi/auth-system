package tr.gov.kurum.identity.application.port.in;

import java.time.Instant;
import java.util.List;

/** A support grant with both parties' e-mail addresses and its extension history (audit). */
public record SupportGrantView(
        long id,
        long agentId,
        long customerId,
        String agentEmail,
        String customerEmail,
        String status,
        List<String> scopes,
        int initialMinutes,
        int extensionCount,
        int totalApprovedExtensionMinutes,
        Instant expiresAt,
        Instant requestedAt,
        Instant startedAt,
        Instant endedAt,
        String endReason,
        List<SupportGrantExtensionView> extensions) {
}
