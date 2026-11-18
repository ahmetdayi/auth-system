package tr.gov.kurum.identity.domain.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import tr.gov.kurum.common.domain.BusinessRuleException;
import tr.gov.kurum.common.domain.PermissionCodes;

/**
 * Whitelist of scopes a support agent may request. Customer sees and consents to exactly this set;
 * the act-as JWT carries only the approved scopes (plus {@code TYPE_CUSTOMER}).
 */
public final class SupportScopes {

    public record Entry(String code, String label, String description) {
    }

    /** Ordered catalog shown in the console / API. */
    public static final List<Entry> CATALOG = List.of(
        new Entry(PermissionCodes.SUPPORT_PROFILE_READ, "Profil okuma",
            "Müşteri ad, e-posta, puan ve adreslerini görüntüleme"),
        new Entry(PermissionCodes.SUPPORT_PROFILE_WRITE, "Profil yazma",
            "Ad güncelleme ve adres ekleme/silme (şifre hariç)"),
        new Entry(PermissionCodes.SUPPORT_CONTENT_READ, "İçerik okuma",
            "Müşterinin üyelik seviyesine göre makale / premium içerik okuma"),
        new Entry(PermissionCodes.SUPPORT_FULL, "Tam müşteri erişimi",
            "Müşterinin yapabildiği her şey (şifre dahil); grant onay/red yalnızca müşterinin kendi oturumu"));

    public static final List<String> DEFAULT = CATALOG.stream().map(Entry::code).toList();

    private static final Set<String> ALLOWED = Set.copyOf(DEFAULT);

    private SupportScopes() {
    }

    public static Set<String> all() {
        return new LinkedHashSet<>(DEFAULT);
    }

    public static Set<String> requireValid(Set<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return all();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String scope : requested) {
            if (scope == null || !ALLOWED.contains(scope)) {
                throw new BusinessRuleException(IdentityErrorKey.SUPPORT_SCOPE_INVALID, String.valueOf(scope));
            }
            normalized.add(scope);
        }
        return normalized;
    }
}
