package tr.gov.kurum.identity.application.port.in;

import java.util.Objects;
import tr.gov.kurum.common.domain.AccountType;
import tr.gov.kurum.identity.domain.model.ClientDetails;

public record AuthenticateCommand(String email, String password, AccountType accountType, ClientDetails client) {

    public AuthenticateCommand {
        Objects.requireNonNull(accountType, "accountType");
        Objects.requireNonNull(client, "client");
    }

    @Override
    public String toString() {
        return "AuthenticateCommand[email=" + email + ", accountType=" + accountType + ", password=PROTECTED]";
    }
}
