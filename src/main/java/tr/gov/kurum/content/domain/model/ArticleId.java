package tr.gov.kurum.content.domain.model;

import tr.gov.kurum.common.domain.BusinessRuleException;

public record ArticleId(long value) {

    public ArticleId {
        if (value <= 0) {
            throw new BusinessRuleException(ContentErrorKey.INVALID_ARTICLE, value);
        }
    }
}
