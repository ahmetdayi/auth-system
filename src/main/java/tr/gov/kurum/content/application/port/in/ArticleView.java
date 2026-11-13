package tr.gov.kurum.content.application.port.in;

import java.time.Instant;
import tr.gov.kurum.content.domain.model.Article;
import tr.gov.kurum.content.domain.model.ReaderTier;

public record ArticleView(long id, String title, String body, ReaderTier requiredTier, Instant publishedAt) {

    public static ArticleView of(Article article) {
        return new ArticleView(article.id().value(), article.title(), article.body(), article.requiredTier(),
            article.publishedAt());
    }
}
