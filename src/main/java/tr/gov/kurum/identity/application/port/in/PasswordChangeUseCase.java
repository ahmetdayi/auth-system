package tr.gov.kurum.identity.application.port.in;

/** Any account changes its own password; every session of the account ends. */
public interface PasswordChangeUseCase {

    void changePassword(ChangePasswordCommand command);
}
