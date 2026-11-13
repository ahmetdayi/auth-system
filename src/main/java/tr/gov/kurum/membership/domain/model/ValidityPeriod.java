package tr.gov.kurum.membership.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import tr.gov.kurum.common.domain.BusinessRuleException;

/** Half-open interval {@code [startInclusive, endExclusive)}. A missing end means open-ended. */
public record ValidityPeriod(Instant startInclusive, Instant endExclusive) {

    public ValidityPeriod {
        Objects.requireNonNull(startInclusive, "startInclusive");
        if (endExclusive != null && !endExclusive.isAfter(startInclusive)) {
            throw new BusinessRuleException(MembershipErrorKey.INVALID_MEMBERSHIP_PERIOD);
        }
    }

    public static ValidityPeriod openEnded(Instant start) {
        return new ValidityPeriod(start, null);
    }

    public boolean contains(Instant instant) {
        return !instant.isBefore(startInclusive) && (endExclusive == null || instant.isBefore(endExclusive));
    }

    public Optional<Instant> end() {
        return Optional.ofNullable(endExclusive);
    }
}
