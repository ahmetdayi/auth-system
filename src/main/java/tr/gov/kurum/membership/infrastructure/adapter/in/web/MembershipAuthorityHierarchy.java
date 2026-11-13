package tr.gov.kurum.membership.infrastructure.adapter.in.web;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tr.gov.kurum.common.web.AuthorityHierarchyContribution;
import tr.gov.kurum.membership.domain.model.MembershipTier;

/**
 * Higher tiers include lower ones: GOLD &gt; SILVER &gt; BRONZE, derived from the enum order so a new tier
 * cannot be forgotten here. The {@code MEMBERSHIP_} prefix is the authority naming contract shared with the
 * identity context (which puts the authority into the token) and the content context (which checks it).
 */
@Component
class MembershipAuthorityHierarchy implements AuthorityHierarchyContribution {

    static final String MEMBERSHIP_PREFIX = "MEMBERSHIP_";

    @Override
    public List<String> hierarchyLines() {
        MembershipTier[] tiers = MembershipTier.values();
        List<String> lines = new ArrayList<>();
        for (int i = tiers.length - 1; i > 0; i--) {
            lines.add(MEMBERSHIP_PREFIX + tiers[i].name() + " > " + MEMBERSHIP_PREFIX + tiers[i - 1].name());
        }
        return List.copyOf(lines);
    }
}
