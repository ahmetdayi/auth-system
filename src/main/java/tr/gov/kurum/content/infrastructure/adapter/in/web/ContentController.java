package tr.gov.kurum.content.infrastructure.adapter.in.web;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tr.gov.kurum.common.domain.PermissionCodes;
import tr.gov.kurum.content.application.port.in.ArticleSummaryView;
import tr.gov.kurum.content.application.port.in.ArticleView;
import tr.gov.kurum.content.application.port.in.ContentUseCase;
import tr.gov.kurum.content.application.port.in.DiscountView;
import tr.gov.kurum.content.domain.model.ArticleId;
import tr.gov.kurum.content.domain.model.ReaderTier;

/**
 * Three ways of restricting content, side by side:
 * <ul>
 *   <li>{@code /articles/{id}}: a domain rule ({@code Article.requireReadableBy}) — 403 with a specific code;</li>
 *   <li>{@code /tiers/{tier}/articles}: {@code @PreAuthorize} on the membership authority; the role hierarchy
 *       lets GOLD read SILVER and BRONZE lists;</li>
 *   <li>{@code /premium/articles}: a permission that comes from a role, independent of membership.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/content")
@PreAuthorize("hasAuthority('TYPE_CUSTOMER')")
class ContentController {

    private final ContentUseCase content;

    ContentController(ContentUseCase content) {
        this.content = content;
    }

    @GetMapping("/articles")
    List<ArticleSummaryView> articles(Authentication authentication) {
        return content.listArticles(ReaderTierResolver.of(authentication));
    }

    @GetMapping("/articles/{articleId}")
    ArticleView article(@PathVariable long articleId, Authentication authentication) {
        return content.readArticle(new ArticleId(articleId), ReaderTierResolver.of(authentication));
    }

    @GetMapping("/tiers/{tier}/articles")
    @PreAuthorize("hasAuthority('TYPE_CUSTOMER') and hasAuthority('MEMBERSHIP_' + #tier.name())")
    List<ArticleSummaryView> articlesOfTier(@PathVariable ReaderTier tier, Authentication authentication) {
        return content.listArticlesRequiring(tier, ReaderTierResolver.of(authentication));
    }

    @GetMapping("/premium/articles")
    @PreAuthorize("hasAuthority('TYPE_CUSTOMER') and hasAuthority('" + PermissionCodes.CONTENT_PREMIUM_READ + "')")
    List<ArticleSummaryView> premiumArticles(Authentication authentication) {
        return content.listArticlesRequiring(ReaderTier.GOLD, ReaderTierResolver.of(authentication));
    }

    @GetMapping("/discount")
    DiscountView discount(Authentication authentication) {
        return content.discountFor(ReaderTierResolver.of(authentication));
    }
}
