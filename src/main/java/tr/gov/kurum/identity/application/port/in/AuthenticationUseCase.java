package tr.gov.kurum.identity.application.port.in;

import tr.gov.kurum.common.domain.AccountId;

/** Token lifecycle: login, refresh (rotation), logout. */
public interface AuthenticationUseCase {

    AuthenticationResult authenticate(AuthenticateCommand command);

    AuthenticationResult refresh(RefreshSessionCommand command);

    void logout(LogoutCommand command);

    /** Ends every session of the account and revokes all access tokens issued so far. */
    void logoutEverywhere(AccountId account);
}
