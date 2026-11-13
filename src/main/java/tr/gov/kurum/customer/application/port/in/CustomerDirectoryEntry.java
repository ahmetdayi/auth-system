package tr.gov.kurum.customer.application.port.in;

import java.time.Instant;

/** One row of the customer list. A read model (rule 3.4): produced by a projection, no aggregate loaded. */
public record CustomerDirectoryEntry(long id, String contactEmail, String firstName, String lastName,
                                     Instant registeredAt) {
}
