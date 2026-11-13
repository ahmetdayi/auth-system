package tr.gov.kurum.identity.application.port.in;

import java.time.Instant;
import tr.gov.kurum.common.domain.AccountType;
import tr.gov.kurum.identity.domain.model.AccountStatus;

/** Login data of an account, without anything secret. A read model (rule 3.4): no aggregate is loaded. */
public record AccountSummaryView(long id, String email, AccountType type, AccountStatus status, Instant createdAt) {
}
