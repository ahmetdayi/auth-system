package tr.gov.kurum.customer.application.port.in;

/** Creates the profile of a newly opened customer account. */
public interface CustomerOnboardingUseCase {

    /**
     * Idempotent: if the profile already exists nothing happens, so a redelivered event is harmless.
     *
     * @return {@code true} if the profile was created by this call
     */
    boolean openProfile(OpenCustomerProfileCommand command);
}
