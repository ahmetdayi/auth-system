package tr.gov.kurum.identity.domain.model;

import tr.gov.kurum.common.domain.PermissionCodes;

/**
 * Catalog of permissions known to the application: code (authority string), human description and UI group.
 * Synced into the {@code permissions} table at startup; assignments still live in
 * {@code account_permissions} / {@code role_permissions}.
 */
public enum KnownPermission {

    PROFILE_READ(PermissionCodes.PROFILE_READ, "Kendi profil bilgisini görüntüleme", "profile"),
    PROFILE_WRITE(PermissionCodes.PROFILE_WRITE, "Kendi profil bilgisini güncelleme", "profile"),
    CUSTOMER_READ(PermissionCodes.CUSTOMER_READ, "Müşteri verilerini görüntüleme", "customer"),
    CUSTOMER_WRITE(PermissionCodes.CUSTOMER_WRITE, "Müşteri verilerini güncelleme", "customer"),
    CONTENT_PREMIUM_READ(PermissionCodes.CONTENT_PREMIUM_READ, "Premium içerik okuma", "content"),
    SUPPORT_PROFILE_READ(PermissionCodes.SUPPORT_PROFILE_READ, "Destek oturumunda profil okuma", "support"),
    SUPPORT_PROFILE_WRITE(PermissionCodes.SUPPORT_PROFILE_WRITE, "Destek oturumunda profil güncelleme", "support"),
    SUPPORT_CONTENT_READ(PermissionCodes.SUPPORT_CONTENT_READ, "Destek oturumunda müşteri içeriklerini okuma", "support"),
    SUPPORT_FULL(PermissionCodes.SUPPORT_FULL, "Destek oturumunda müşterinin tüm yetkileri (şifre dahil)", "support");

    private final String code;
    private final String description;
    private final String group;

    KnownPermission(String code, String description, String group) {
        this.code = code;
        this.description = description;
        this.group = group;
    }

    public String code() {
        return code;
    }

    public String description() {
        return description;
    }

    public String group() {
        return group;
    }

    public PermissionName toPermissionName() {
        return new PermissionName(code);
    }
}
