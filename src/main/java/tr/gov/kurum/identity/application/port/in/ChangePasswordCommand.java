package tr.gov.kurum.identity.application.port.in;

import java.util.Objects;
import tr.gov.kurum.common.domain.AccountId;

/** @param clientIp used for the brute-force counter, like at login */
public record ChangePasswordCommand(
        AccountId accountId,
        String currentPassword,
        String newPassword,
        String clientIp) {

    public ChangePasswordCommand {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(clientIp, "clientIp");
    }

    @Override
    public String toString() {
        return "ChangePasswordCommand[accountId=" + accountId.value() + ", passwords=PROTECTED]";
    }
}
