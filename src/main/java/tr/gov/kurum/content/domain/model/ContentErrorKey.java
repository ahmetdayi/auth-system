package tr.gov.kurum.content.domain.model;

import tr.gov.kurum.common.domain.ErrorCategory;
import tr.gov.kurum.common.domain.ErrorKey;

public enum ContentErrorKey implements ErrorKey {

    INVALID_ARTICLE(ErrorCategory.INVALID_INPUT),
    ARTICLE_NOT_FOUND(ErrorCategory.NOT_FOUND),
    ARTICLE_REQUIRES_HIGHER_TIER(ErrorCategory.ACCESS_DENIED);

    private final ErrorCategory category;

    ContentErrorKey(ErrorCategory category) {
        this.category = category;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public ErrorCategory category() {
        return category;
    }
}
