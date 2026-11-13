package tr.gov.kurum.common.web;

import java.util.List;

/**
 * Lines of the authority hierarchy ({@code "HIGHER > LOWER"}) a bounded context contributes. The security
 * configuration combines every contribution, so a context declares the ordering of its own authorities
 * (e.g. membership tiers) without the security configuration knowing them.
 */
@FunctionalInterface
public interface AuthorityHierarchyContribution {

    List<String> hierarchyLines();
}
