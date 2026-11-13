package tr.gov.kurum.content.domain.model;

import java.time.Instant;
import java.util.Objects;
import tr.gov.kurum.common.domain.AggregateRoot;
import tr.gov.kurum.common.domain.BusinessRuleException;

/** An article that may require a minimum reader tier. */
public final class Article extends AggregateRoot {

    private final ArticleId id;
    private final String title;
    private final String summary;
    private final String body;
    private final ReaderTier requiredTier;
    private final Instant publishedAt;

    private Article(ArticleId id, long version, String title, String summary, String body,
                    ReaderTier requiredTier, Instant publishedAt) {
        super(version);
        this.id = Objects.requireNonNull(id, "id");
        this.title = requireText(title);
        this.summary = requireText(summary);
        this.body = requireText(body);
        this.requiredTier = Objects.requireNonNull(requiredTier, "requiredTier");
        this.publishedAt = Objects.requireNonNull(publishedAt, "publishedAt");
    }

    public static Article restore(ArticleId id, long version, String title, String summary, String body,
                                  ReaderTier requiredTier, Instant publishedAt) {
        return new Article(id, version, title, summary, body, requiredTier, publishedAt);
    }

    public boolean isReadableBy(ReaderTier reader) {
        return reader.satisfies(requiredTier);
    }

    public void requireReadableBy(ReaderTier reader) {
        if (!isReadableBy(reader)) {
            throw new BusinessRuleException(ContentErrorKey.ARTICLE_REQUIRES_HIGHER_TIER, requiredTier.name());
        }
    }

    public ArticleId id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String summary() {
        return summary;
    }

    public String body() {
        return body;
    }

    public ReaderTier requiredTier() {
        return requiredTier;
    }

    public Instant publishedAt() {
        return publishedAt;
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException(ContentErrorKey.INVALID_ARTICLE);
        }
        return value;
    }
}
