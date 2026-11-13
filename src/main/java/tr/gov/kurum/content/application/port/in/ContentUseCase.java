package tr.gov.kurum.content.application.port.in;

import java.util.List;
import tr.gov.kurum.content.domain.model.ArticleId;
import tr.gov.kurum.content.domain.model.ReaderTier;

public interface ContentUseCase {

    /** Latest articles; those above the reader's tier are listed as locked. */
    List<ArticleSummaryView> listArticles(ReaderTier reader);

    /** Articles that require exactly {@code tier}. */
    List<ArticleSummaryView> listArticlesRequiring(ReaderTier tier, ReaderTier reader);

    ArticleView readArticle(ArticleId articleId, ReaderTier reader);

    DiscountView discountFor(ReaderTier reader);
}
