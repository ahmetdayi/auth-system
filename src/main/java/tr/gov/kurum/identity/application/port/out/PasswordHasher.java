package tr.gov.kurum.identity.application.port.out;

import tr.gov.kurum.identity.domain.model.PasswordHash;
import tr.gov.kurum.identity.domain.model.PlainTextPassword;

public interface PasswordHasher {

    PasswordHash hash(PlainTextPassword password);

    /**
     * Compares in constant work: when {@code storedHash} is {@code null} (unknown account) the password is
     * still checked against an internal dummy hash, so response time does not reveal whether the account
     * exists. Passwords longer than the hashing limit never match.
     */
    boolean verify(PlainTextPassword password, PasswordHash storedHash);
}
