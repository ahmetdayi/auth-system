package tr.gov.kurum.content.infrastructure.adapter.in.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import tr.gov.kurum.content.domain.model.ReaderTier;

/**
 * Anti-corruption layer between the identity context and this one. The content context does not import the
 * identity context's membership type; it only reads the authority strings of the caller's token, which are
 * the contract between the two, and translates them into its own {@link ReaderTier}.
 *
 * <p>Reads the authorities as granted (not expanded by the role hierarchy) and takes the highest tier.</p>
 */
final class ReaderTierResolver {

    private ReaderTierResolver() {
    }

    static ReaderTier of(Authentication authentication) {
        ReaderTier best = ReaderTier.NONE;
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            ReaderTier tier = switch (authority.getAuthority()) {
                case "MEMBERSHIP_BRONZE" -> ReaderTier.BRONZE;
                case "MEMBERSHIP_SILVER" -> ReaderTier.SILVER;
                case "MEMBERSHIP_GOLD" -> ReaderTier.GOLD;
                case null, default -> ReaderTier.NONE;
            };
            if (tier.satisfies(best)) {
                best = tier;
            }
        }
        return best;
    }
}
