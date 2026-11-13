package tr.gov.kurum.console.infrastructure.adapter.in.web;

import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Thymeleaf test console. Pages are shells: every action on them calls the REST API from the browser, so
 * the console exercises exactly the endpoints a real client would use, with the same security rules.
 *
 * <p>This module depends on no other bounded context's code; it only knows URLs and what Spring Security
 * puts into the request (the authentication's name and authorities).</p>
 */
@Controller
@EnableConfigurationProperties(ConsoleProperties.class)
class ConsoleController {

    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();
    private final ConsoleProperties properties;

    ConsoleController(ConsoleProperties properties) {
        this.properties = properties;
    }

    /** Server-side view of the caller, rendered into the header. {@code null} for anonymous visitors. */
    @ModelAttribute("currentUser")
    CurrentUserModel currentUser(Authentication authentication) {
        if (authentication == null || trustResolver.isAnonymous(authentication)) {
            return null;
        }
        List<String> authorities = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .sorted()
            .toList();
        return new CurrentUserModel(authentication.getName(), authorities);
    }

    @GetMapping("/")
    String root() {
        return "redirect:/console";
    }

    @GetMapping("/console")
    String home(Model model) {
        model.addAttribute("showDemoAccounts", properties.showDemoAccounts());
        model.addAttribute("demoAccounts", properties.showDemoAccounts() ? DemoAccount.ALL : List.of());
        return page(model, "home");
    }

    @GetMapping("/console/login")
    String login(Model model) {
        model.addAttribute("demoAccounts", properties.showDemoAccounts() ? DemoAccount.ALL : List.of());
        return page(model, "login");
    }

    @GetMapping("/console/register")
    String register(Model model) {
        return page(model, "register");
    }

    @GetMapping("/console/profile")
    String profile(Model model) {
        return page(model, "profile");
    }

    @GetMapping("/console/sessions")
    String sessions(Model model) {
        return page(model, "sessions");
    }

    @GetMapping("/console/content")
    String content(Model model) {
        return page(model, "content");
    }

    @GetMapping("/console/admin")
    String admin(Model model) {
        return page(model, "admin");
    }

    @GetMapping("/console/mobile")
    String mobile(Model model) {
        model.addAttribute("demoAccounts", properties.showDemoAccounts() ? DemoAccount.ALL : List.of());
        return page(model, "mobile");
    }

    @GetMapping("/console/scenarios")
    String scenarios(Model model) {
        model.addAttribute("showDemoAccounts", properties.showDemoAccounts());
        return page(model, "scenarios");
    }

    private static String page(Model model, String name) {
        model.addAttribute("page", name);
        return "console/" + name;
    }

    public record CurrentUserModel(String name, List<String> authorities) {

        public CurrentUserModel {
            authorities = List.copyOf(authorities);
        }
    }

    /** Mirrors db/demo/R__demo_data.sql; shown only when {@code app.console.show-demo-accounts} is true. */
    public record DemoAccount(String email, String userType, String description) {

        static final String PASSWORD = "DemoSifre123!";

        static final List<DemoAccount> ALL = List.of(
            new DemoAccount("ayse@example.com", "CUSTOMER", "GOLD üye, PREMIUM_READER rolü, 2 adres"),
            new DemoAccount("mehmet@example.com", "CUSTOMER", "SILVER üye"),
            new DemoAccount("can@example.com", "CUSTOMER", "Üyeliği yok"),
            new DemoAccount("zeynep@example.com", "CUSTOMER", "GOLD üyeliği 31.01.2026 tarihinde bitti"),
            new DemoAccount("pasif@example.com", "CUSTOMER", "Devre dışı hesap: doğru şifreyle de giremez"),
            new DemoAccount("admin@example.com", "ADMIN", "Standart admin: customer:read + customer:write"),
            new DemoAccount("super@example.com", "ADMIN", "SUPER_ADMIN: diğer adminlerin oturumlarını yönetir"),
            new DemoAccount("destek@example.com", "ADMIN", "Yalnızca customer:read (yazma işlemleri 403)"));

        public String password() {
            return PASSWORD;
        }
    }
}
