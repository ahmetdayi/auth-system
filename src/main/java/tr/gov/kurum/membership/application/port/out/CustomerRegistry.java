package tr.gov.kurum.membership.application.port.out;

import tr.gov.kurum.common.domain.AccountId;

/**
 * Whether a customer exists, owned by the customer context. Outbound port (rule 5.1): this context states
 * the question, an adapter asks the other context.
 */
public interface CustomerRegistry {

    boolean exists(AccountId customer);
}
