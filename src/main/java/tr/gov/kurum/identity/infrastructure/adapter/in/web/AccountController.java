package tr.gov.kurum.identity.infrastructure.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tr.gov.kurum.common.domain.PermissionCodes;
import tr.gov.kurum.common.web.AuthenticatedAccount;
import tr.gov.kurum.common.web.CurrentAccount;
import tr.gov.kurum.identity.application.port.in.ChangePasswordCommand;
import tr.gov.kurum.identity.application.port.in.PasswordChangeUseCase;
import tr.gov.kurum.identity.infrastructure.adapter.in.web.AuthDtos.MeResponse;
import tr.gov.kurum.identity.infrastructure.adapter.in.web.security.AuthCookies;

/** The caller's own account. Open to every account type. */
@RestController
@RequestMapping("/api")
class AccountController {

    private final PasswordChangeUseCase passwords;
    private final AuthCookies cookies;

    AccountController(PasswordChangeUseCase passwords, AuthCookies cookies) {
        this.passwords = passwords;
        this.cookies = cookies;
    }

    /** Answers from the access token alone: no database access. */
    @GetMapping("/me")
    MeResponse me(@CurrentAccount AuthenticatedAccount account, Authentication authentication) {
        List<String> authorities = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .sorted()
            .toList();
        return MeResponse.of(account, authorities);
    }

    /**
     * Ends every session, this one included; the browser's cookies are removed and the user logs in again.
     * Same endpoint for customers and admins: a password belongs to the account, not to a profile.
     * With {@code support:full}, an agent may change the customer's password (needs current password).
     */
    @PostMapping("/account/password")
    ResponseEntity<Void> changePassword(@CurrentAccount AuthenticatedAccount account,
                                        Authentication authentication,
                                        @RequestBody @Valid ChangePasswordRequest body,
                                        HttpServletRequest request, HttpServletResponse response) {
        if (account.isSupportSession() && !hasSupportFull(authentication)) {
            account.refuseIfSupportSession();
        }
        passwords.changePassword(new ChangePasswordCommand(account.accountId(), body.currentPassword(),
            body.newPassword(), ClientDetailsResolver.from(request).ipAddress()));
        cookies.clear(response);
        return ResponseEntity.noContent().build();
    }

    private static boolean hasSupportFull(Authentication authentication) {
        return authentication.getAuthorities().stream()
            .anyMatch(a -> PermissionCodes.SUPPORT_FULL.equals(a.getAuthority()));
    }

    record ChangePasswordRequest(
            @NotBlank @Size(max = 200) String currentPassword,
            @NotBlank @Size(max = 200) String newPassword) {

        @Override
        public String toString() {
            return "ChangePasswordRequest[passwords=PROTECTED]";
        }
    }
}
