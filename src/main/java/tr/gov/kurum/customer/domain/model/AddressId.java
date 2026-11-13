package tr.gov.kurum.customer.domain.model;

import tr.gov.kurum.common.domain.BusinessRuleException;
import tr.gov.kurum.common.domain.CommonErrorKey;

public record AddressId(long value) {

    public AddressId {
        if (value <= 0) {
            throw new BusinessRuleException(CommonErrorKey.INVALID_IDENTIFIER, value);
        }
    }
}
