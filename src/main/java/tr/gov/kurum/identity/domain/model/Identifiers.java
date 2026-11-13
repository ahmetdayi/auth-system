package tr.gov.kurum.identity.domain.model;

import tr.gov.kurum.common.domain.BusinessRuleException;
import tr.gov.kurum.common.domain.CommonErrorKey;

/** Shared validation for the strongly-typed identifiers of this context. */
final class Identifiers {

    private Identifiers() {
    }

    static void requirePositive(long value) {
        if (value <= 0) {
            throw new BusinessRuleException(CommonErrorKey.INVALID_IDENTIFIER, value);
        }
    }
}
