package tr.gov.kurum.identity.application.port.in;

import java.time.Instant;

public record SupportGrantExtensionView(
        long id,
        int requestedMinutes,
        Instant requestedAt,
        String decision,
        Instant decidedAt) {
}
