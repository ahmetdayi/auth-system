package tr.gov.kurum.identity.application.port.in;

import java.util.Objects;
import tr.gov.kurum.identity.domain.model.ClientDetails;

public record RefreshSessionCommand(String refreshToken, ClientDetails client) {

    public RefreshSessionCommand {
        Objects.requireNonNull(client, "client");
    }

    @Override
    public String toString() {
        return "RefreshSessionCommand[refreshToken=PROTECTED]";
    }
}
