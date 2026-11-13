package tr.gov.kurum.identity.infrastructure.adapter.in.web.security;

import java.util.ArrayList;
import java.util.List;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authorization.DefaultAuthorizationManagerFactory;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import tr.gov.kurum.common.web.AuthorityHierarchyContribution;
import tr.gov.kurum.identity.domain.model.RoleName;
import tr.gov.kurum.identity.infrastructure.adapter.out.token.AuthorityNames;

/**
 * {@code @PreAuthorize} is evaluated by the {@link MethodSecurityExpressionHandler}; the hierarchy must reach
 * it to apply. Spring Security 7 deprecates {@code setRoleHierarchy} on the handler: the hierarchy goes into the
 * {@link DefaultAuthorizationManagerFactory} that builds {@code hasRole}/{@code hasAuthority} checks.
 * Both beans are static so they exist before method security is configured.
 *
 * <p>The admin line is the identity context's own; other contexts contribute theirs through
 * {@link AuthorityHierarchyContribution} beans (e.g. membership tiers), so this class does not know them.</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class MethodSecurityConfig {

    @Bean
    static RoleHierarchy roleHierarchy(List<AuthorityHierarchyContribution> contributions) {
        String superAdmin = AuthorityNames.ROLE_PREFIX + RoleName.SUPER_ADMIN.value();
        String admin = AuthorityNames.ROLE_PREFIX + RoleName.ADMIN.value();
        List<String> lines = new ArrayList<>();
        lines.add(superAdmin + " > " + admin);
        contributions.forEach(contribution -> lines.addAll(contribution.hierarchyLines()));
        // fromHierarchy takes the lines as they are (no prefix is added).
        return RoleHierarchyImpl.fromHierarchy(String.join("\n", lines));
    }

    @Bean
    static MethodSecurityExpressionHandler methodSecurityExpressionHandler(RoleHierarchy roleHierarchy) {
        DefaultAuthorizationManagerFactory<MethodInvocation> factory = new DefaultAuthorizationManagerFactory<>();
        factory.setRoleHierarchy(roleHierarchy);
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setAuthorizationManagerFactory(factory);
        return handler;
    }
}
