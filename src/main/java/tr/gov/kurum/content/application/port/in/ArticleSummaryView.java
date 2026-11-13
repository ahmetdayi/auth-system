package tr.gov.kurum.content.application.port.in;

import java.time.Instant;
import tr.gov.kurum.content.domain.model.Article;
import tr.gov.kurum.content.domain.model.ReaderTier;

public record ArticleSummaryView(
        long id,
        String title,
        String summary,
        ReaderTier requiredTier,
        Instant publishedAt,
        boolean locked) {

    public static ArticleSummaryView of(Article article, ReaderTier reader) {
        return new ArticleSummaryView(article.id().value(), article.title(), article.summary(),
            article.requiredTier(), article.publishedAt(), !article.isReadableBy(reader));
    }
}
