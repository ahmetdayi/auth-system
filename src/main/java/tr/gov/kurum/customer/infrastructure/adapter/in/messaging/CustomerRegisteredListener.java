package tr.gov.kurum.customer.infrastructure.adapter.in.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tr.gov.kurum.common.infrastructure.adapter.in.messaging.DeliveredEvent;
import tr.gov.kurum.contracts.identity.CustomerRegistered;
import tr.gov.kurum.customer.application.port.in.CustomerOnboardingUseCase;
import tr.gov.kurum.customer.application.port.in.OpenCustomerProfileCommand;

/**
 * Inbound adapter: the identity context opened a customer account, so this context creates its profile.
 * Reads only the published language ({@code tr.gov.kurum.contracts}), never identity's code or tables.
 *
 * <p>Runs in the outbox relay's transaction. If it throws, the relay rolls back and retries later with
 * back-off; {@link CustomerOnboardingUseCase#openProfile} is idempotent, so a redelivery is harmless.</p>
 */
@Component
class CustomerRegisteredListener {

    private static final Logger log = LoggerFactory.getLogger(CustomerRegisteredListener.class);

    private final CustomerOnboardingUseCase onboarding;

    CustomerRegisteredListener(CustomerOnboardingUseCase onboarding) {
        this.onboarding = onboarding;
    }

    @EventListener
    void on(DeliveredEvent<CustomerRegistered> delivered) {
        CustomerRegistered event = delivered.event();
        boolean created = onboarding.openProfile(new OpenCustomerProfileCommand(event.accountId(), event.email(),
            event.firstName(), event.lastName(), event.occurredAt()));
        if (created) {
            log.info("Customer profile opened: account={}", event.accountId().value());
        } else {
            log.debug("Customer profile already exists, event ignored: account={}", event.accountId().value());
        }
    }
}
