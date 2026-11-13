package tr.gov.kurum.content.application.port.out;

import java.util.List;
import java.util.Optional;
import tr.gov.kurum.content.domain.model.Article;
import tr.gov.kurum.content.domain.model.ArticleId;
import tr.gov.kurum.content.domain.model.ReaderTier;

public interface ArticleRepository {

    /** Newest first; bounded so the list cannot grow without limit. */
    List<Article> findLatest(int limit);

    List<Article> findLatestRequiring(ReaderTier tier, int limit);

    Optional<Article> findById(ArticleId id);
}
