package tr.gov.kurum.identity.infrastructure.adapter.out.token;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tr.gov.kurum.identity.domain.model.SessionPolicy;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TokenProperties.class)
public class TokenConfig {

    @Bean
    SessionPolicy sessionPolicy(TokenProperties properties) {
        return new SessionPolicy(properties.refreshTokenTtl(), properties.rotationGracePeriod());
    }
}
