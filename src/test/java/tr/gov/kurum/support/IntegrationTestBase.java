package tr.gov.kurum.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * Full application on real PostgreSQL and Redis, driven through MockMvc with the real security filter chain.
 * All subclasses share one Spring context (same configuration), so the suite starts it once.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTestBeans.class)
public abstract class IntegrationTestBase {

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        TestInfrastructure.register(registry);
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JsonMapper jsonMapper;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected TestData data;

    /** A new client (cookie jar, own IP), like a separate browser or device. */
    protected ApiClient client() {
        return new ApiClient(mvc, jsonMapper, TestData.uniqueIp());
    }

    /** Logs a browser client in with cookies. */
    protected ApiClient browserOf(TestData.Account account) {
        ApiClient browser = client();
        ApiClient.Response response = browser.browserLogin(account.email(), TestData.PASSWORD, account.type());
        if (response.status() != 200) {
            throw new IllegalStateException("login failed: " + response.status() + " " + response.code());
        }
        return browser;
    }

    /** Logs a mobile client in; it sends the access token as Bearer. */
    protected ApiClient mobileOf(TestData.Account account) {
        ApiClient mobile = client();
        ApiClient.Response response = mobile.mobileLogin(account.email(), TestData.PASSWORD, account.type());
        if (response.status() != 200) {
            throw new IllegalStateException("login failed: " + response.status() + " " + response.code());
        }
        return mobile;
    }
}
