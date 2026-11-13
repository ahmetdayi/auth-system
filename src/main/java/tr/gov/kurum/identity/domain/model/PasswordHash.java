package tr.gov.kurum.identity.domain.model;

import java.util.Objects;

/** One-way password hash produced by the {@code PasswordHasher} port. Masked in {@link #toString()}. */
public record PasswordHash(String value) {

    public PasswordHash {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("password hash must not be blank");
        }
    }

    @Override
    public String toString() {
        return "PasswordHash[PROTECTED]";
    }
}
