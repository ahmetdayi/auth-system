package tr.gov.kurum.content.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tr.gov.kurum.common.domain.EntityNotFoundException;
import tr.gov.kurum.content.application.port.in.ArticleSummaryView;
import tr.gov.kurum.content.application.port.in.ArticleView;
import tr.gov.kurum.content.application.port.in.ContentUseCase;
import tr.gov.kurum.content.application.port.in.DiscountView;
import tr.gov.kurum.content.application.port.out.ArticleRepository;
import tr.gov.kurum.content.domain.model.Article;
import tr.gov.kurum.content.domain.model.ArticleId;
import tr.gov.kurum.content.domain.model.ContentErrorKey;
import tr.gov.kurum.content.domain.model.ReaderTier;

@Service
@RequiredArgsConstructor
public class ContentService implements ContentUseCase {

    private static final int MAX_LISTED_ARTICLES = 50;

    private final ArticleRepository articleRepository;

    @Override
    public List<ArticleSummaryView> listArticles(ReaderTier reader) {
        return articleRepository.findLatest(MAX_LISTED_ARTICLES).stream()
            .map(article -> ArticleSummaryView.of(article, reader))
            .toList();
    }

    @Override
    public List<ArticleSummaryView> listArticlesRequiring(ReaderTier tier, ReaderTier reader) {
        return articleRepository.findLatestRequiring(tier, MAX_LISTED_ARTICLES).stream()
            .map(article -> ArticleSummaryView.of(article, reader))
            .toList();
    }

    @Override
    public ArticleView readArticle(ArticleId articleId, ReaderTier reader) {
        Article article = articleRepository.findById(articleId)
            .orElseThrow(() -> new EntityNotFoundException(ContentErrorKey.ARTICLE_NOT_FOUND, articleId.value()));
        article.requireReadableBy(reader);
        return ArticleView.of(article);
    }

    @Override
    public DiscountView discountFor(ReaderTier reader) {
        return new DiscountView(reader, reader.discountRate().percent());
    }
}
