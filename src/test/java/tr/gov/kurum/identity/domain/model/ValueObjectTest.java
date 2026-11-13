package tr.gov.kurum.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tr.gov.kurum.common.domain.BusinessRuleException;
import tr.gov.kurum.common.domain.CommonErrorKey;
import tr.gov.kurum.common.domain.EmailAddress;
import tr.gov.kurum.common.domain.ErrorKey;

class ValueObjectTest {

    private static ErrorKey errorOf(Runnable action) {
        try {
            action.run();
        } catch (BusinessRuleException e) {
            return e.errorKey();
        }
        throw new AssertionError("expected a BusinessRuleException");
    }

    @Test
    void email_is_lower_cased_with_root_locale_even_on_a_turkish_jvm() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            // With the Turkish locale "I".toLowerCase() is the dotless "ı": the address would change.
            assertThat(EmailAddress.of("  ILKER@Example.COM ").value()).isEqualTo("ilker@example.com");
        } finally {
            Locale.setDefault(original);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "no-at-sign", "a@b", "a@@example.com", "ayşe@example.com", "a b@example.com"})
    void invalid_emails_are_rejected(String raw) {
        assertThat(errorOf(() -> EmailAddress.of(raw))).isEqualTo(CommonErrorKey.INVALID_EMAIL);
    }

    @Test
    void new_passwords_are_checked_in_characters_and_in_utf8_bytes() {
        assertThat(errorOf(() -> PlainTextPassword.forNewPassword("kisa-sifre1")))     // 11 characters
            .isEqualTo(IdentityErrorKey.PASSWORD_TOO_SHORT);
        // 37 characters, 74 bytes: short enough in characters, too long for BCrypt.
        assertThat(errorOf(() -> PlainTextPassword.forNewPassword("ğ".repeat(37))))
            .isEqualTo(IdentityErrorKey.PASSWORD_TOO_LONG);
        assertThat(PlainTextPassword.forNewPassword("ğ".repeat(36)).fitsHashingLimit()).isTrue();
    }

    @Test
    void passwords_for_verification_are_not_rejected_but_flagged_when_too_long() {
        PlainTextPassword tooLong = PlainTextPassword.forVerification("x".repeat(73));

        assertThat(tooLong.fitsHashingLimit()).isFalse();
        assertThat(tooLong.toString()).doesNotContain("xxx");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "SUPER_ADMIN", "ROLE_USER", "TYPE_ADMIN", "MEMBERSHIP_GOLD", "user", "X"})
    void reserved_or_malformed_role_names_are_rejected(String raw) {
        assertThatThrownBy(() -> RoleName.of(raw)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void ordinary_role_names_are_accepted() {
        assertThat(RoleName.of("PREMIUM_READER").value()).isEqualTo("PREMIUM_READER");
    }

    @ParameterizedTest
    @ValueSource(strings = {"read", "ROLE_ADMIN", "customer:", "Customer:read", "customer read"})
    void malformed_permission_names_are_rejected(String raw) {
        assertThat(errorOf(() -> new PermissionName(raw))).isEqualTo(IdentityErrorKey.INVALID_PERMISSION_NAME);
    }

    @Test
    void refresh_token_hash_is_deterministic_and_never_printed_in_full() {
        RefreshTokenHash hash = RefreshTokenHash.of("raw-token");

        assertThat(RefreshTokenHash.of("raw-token")).isEqualTo(hash);
        assertThat(hash.value()).hasSize(43).doesNotContain("raw-token");   // SHA-256, Base64URL, no padding
        assertThat(hash.toString()).doesNotContain(hash.value());
        assertThat(RefreshTokenHash.tryOf(null)).isEmpty();
        assertThat(RefreshTokenHash.tryOf("  ")).isEmpty();
        assertThat(RefreshTokenHash.tryOf("x".repeat(201))).isEmpty();
    }

    @Test
    void client_details_are_bounded_and_never_empty() {
        ClientDetails details = new ClientDetails("A".repeat(500), null);

        assertThat(details.userAgent()).hasSize(255);
        assertThat(details.ipAddress()).isEqualTo("unknown");
    }
}
