package tr.gov.kurum.common.web;

import java.time.Instant;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.AccountType;

/**
 * The caller of a request, as the identity context's token filter established it. Lives in the web part of
 * the shared kernel so that every context's controllers can use it without depending on the identity context
 * (rule 8: universal identity types). It deliberately carries no profile data.
 *
 * <p>Never serialized (the application is stateless), so it does not implement {@code Serializable}.</p>
 */
public record AuthenticatedAccount(
        AccountId accountId,
        AccountType type,
        long sessionId,
        String email,
        String tokenId,
        Instant tokenExpiresAt) {

    public AuthenticatedAccount {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(tokenId, "tokenId");
        Objects.requireNonNull(tokenExpiresAt, "tokenExpiresAt");
    }

    public boolean is(AccountType expected) {
        return type == expected;
    }

    /** @throws AccessDeniedException (403) when the caller is not a customer */
    public AccountId requireCustomer() {
        return require(AccountType.CUSTOMER);
    }

    /** @throws AccessDeniedException (403) when the caller is not an admin */
    public AccountId requireAdmin() {
        return require(AccountType.ADMIN);
    }

    private AccountId require(AccountType expected) {
        if (type != expected) {
            throw new AccessDeniedException(expected + " endpoint");
        }
        return accountId;
    }

    /** Spring Security uses this as the authentication name (e.g. in logs): no e-mail address in logs. */
    @Override
    public String toString() {
        return type + "#" + accountId.value();
    }
}
