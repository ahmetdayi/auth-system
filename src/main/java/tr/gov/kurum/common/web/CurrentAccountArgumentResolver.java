package tr.gov.kurum.common.web;

import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves {@code @CurrentAccount AuthenticatedAccount}. The exceptions are not handled by the global handler, so
 * Spring Security's ExceptionTranslationFilter turns them into 401.
 */
final class CurrentAccountArgumentResolver implements HandlerMethodArgumentResolver {

    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentAccount.class)
            && AuthenticatedAccount.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // An anonymous authentication is not null and reports isAuthenticated() == true: check explicitly.
        if (authentication == null || trustResolver.isAnonymous(authentication)
                || !(authentication.getPrincipal() instanceof AuthenticatedAccount account)) {
            throw new InsufficientAuthenticationException("Authentication required");
        }
        return account;
    }
}
