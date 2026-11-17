-- Permission catalog: name (authority), description, group. Assignments keep using permission_name strings
-- with a foreign key into this table.

CREATE TABLE permissions (
    name        VARCHAR(100) PRIMARY KEY,
    description VARCHAR(255) NOT NULL,
    group_name  VARCHAR(50)  NOT NULL,
    CONSTRAINT ck_permissions_name_format CHECK (name ~ '^[a-z][a-z0-9-]*(:[a-z][a-z0-9-]*)+$'),
    CONSTRAINT ck_permissions_group_format CHECK (group_name ~ '^[a-z][a-z0-9-]{0,49}$')
);

-- Backfill anything already assigned so the FK can be added safely.
INSERT INTO permissions (name, description, group_name)
SELECT DISTINCT permission_name, permission_name, 'uncategorized'
FROM (
    SELECT permission_name FROM role_permissions
    UNION
    SELECT permission_name FROM account_permissions
) existing
ON CONFLICT (name) DO NOTHING;

-- Canonical rows (match KnownPermission). Overwrite description/group for known codes.
INSERT INTO permissions (name, description, group_name) VALUES
    ('profile:read', 'Kendi profil bilgisini görüntüleme', 'profile'),
    ('profile:write', 'Kendi profil bilgisini güncelleme', 'profile'),
    ('customer:read', 'Müşteri verilerini görüntüleme', 'customer'),
    ('customer:write', 'Müşteri verilerini güncelleme', 'customer'),
    ('content:premium:read', 'Premium içerik okuma', 'content')
ON CONFLICT (name) DO UPDATE
SET description = EXCLUDED.description,
    group_name = EXCLUDED.group_name;

ALTER TABLE role_permissions
    ADD CONSTRAINT fk_role_permissions_permission
        FOREIGN KEY (permission_name) REFERENCES permissions (name);

ALTER TABLE account_permissions
    ADD CONSTRAINT fk_account_permissions_permission
        FOREIGN KEY (permission_name) REFERENCES permissions (name);
