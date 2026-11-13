-- Demo data, loaded ONLY by the "local" profile (spring.flyway.locations adds classpath:db/demo).
-- Repeatable and idempotent: rows are matched by e-mail, never by id, so this runs on a fresh database and
-- on one migrated from the old schema alike, and re-running it never duplicates anything.
-- Every demo account uses the password: DemoSifre123!

-- ------------------------------------------------------------------------------------------ identity
INSERT INTO roles (id, name, created_at, updated_at, version)
SELECT nextval('role_id_seq'), 'PREMIUM_READER', now(), now(), 0
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'PREMIUM_READER');

INSERT INTO role_permissions (role_id, permission_name)
SELECT id, 'content:premium:read' FROM roles WHERE name = 'PREMIUM_READER'
ON CONFLICT DO NOTHING;

-- Ids are handed out in the listed order (nextval() in a SELECT does not follow ORDER BY).
WITH demo (email, account_type, admin_level, status, created_at) AS (VALUES
        ('ayse@example.com',   'CUSTOMER', NULL,       'ACTIVE',   TIMESTAMPTZ '2026-01-01T00:00:00Z'),
        ('mehmet@example.com', 'CUSTOMER', NULL,       'ACTIVE',   TIMESTAMPTZ '2026-01-02T00:00:00Z'),
        ('can@example.com',    'CUSTOMER', NULL,       'ACTIVE',   TIMESTAMPTZ '2026-01-03T00:00:00Z'),
        ('zeynep@example.com', 'CUSTOMER', NULL,       'ACTIVE',   TIMESTAMPTZ '2026-01-04T00:00:00Z'),
        ('pasif@example.com',  'CUSTOMER', NULL,       'DISABLED', TIMESTAMPTZ '2026-01-05T00:00:00Z'),
        ('admin@example.com',  'ADMIN',    'STANDARD', 'ACTIVE',   TIMESTAMPTZ '2026-01-06T00:00:00Z'),
        ('super@example.com',  'ADMIN',    'SUPER',    'ACTIVE',   TIMESTAMPTZ '2026-01-07T00:00:00Z'),
        ('destek@example.com', 'ADMIN',    'STANDARD', 'ACTIVE',   TIMESTAMPTZ '2026-01-08T00:00:00Z')),
missing AS (
    SELECT d.*, row_number() OVER (ORDER BY d.created_at) AS position
    FROM demo d
    WHERE NOT EXISTS (SELECT 1 FROM accounts a WHERE a.email = d.email AND a.account_type = d.account_type)),
base AS (
    SELECT CASE WHEN is_called THEN last_value ELSE 0 END AS last_id FROM account_id_seq)
INSERT INTO accounts (id, email, password_hash, account_type, admin_level, status, sessions_valid_after,
                      created_at, updated_at, version)
SELECT base.last_id + m.position, m.email, '$2a$12$SSQXfQxClbj/n6aS6Jl1RuR1Kw794HqURHv.GKX9zBzcPNBQ3oCXK',
       m.account_type, m.admin_level, m.status, TIMESTAMPTZ '2026-01-01T00:00:00Z', m.created_at, now(), 0
FROM missing m CROSS JOIN base;

SELECT setval('account_id_seq', GREATEST((SELECT max(id) FROM accounts), 1));

INSERT INTO account_roles (account_id, role_id)
SELECT a.id, r.id FROM accounts a JOIN roles r ON r.name = 'USER'
WHERE a.account_type = 'CUSTOMER'
  AND a.email IN ('ayse@example.com', 'mehmet@example.com', 'can@example.com', 'zeynep@example.com',
                  'pasif@example.com')
ON CONFLICT DO NOTHING;

INSERT INTO account_roles (account_id, role_id)
SELECT a.id, r.id FROM accounts a JOIN roles r ON r.name = 'PREMIUM_READER'
WHERE a.account_type = 'CUSTOMER' AND a.email = 'ayse@example.com'
ON CONFLICT DO NOTHING;

INSERT INTO account_permissions (account_id, permission_name)
SELECT a.id, p.permission
FROM accounts a
JOIN (VALUES ('admin@example.com', 'customer:read'), ('admin@example.com', 'customer:write'),
             ('super@example.com', 'customer:read'), ('super@example.com', 'customer:write'),
             ('destek@example.com', 'customer:read')) AS p (email, permission) ON p.email = a.email
WHERE a.account_type = 'ADMIN'
ON CONFLICT DO NOTHING;

-- ------------------------------------------------------------------------------------------ customer
INSERT INTO customer_profiles (account_id, contact_email, first_name, last_name, loyalty_points, registered_at,
                               created_at, updated_at, version)
SELECT a.id, a.email, d.first_name, d.last_name, d.points, a.created_at, now(), now(), 0
FROM accounts a
JOIN (VALUES ('ayse@example.com', 'Ayşe', 'Yılmaz', 1250), ('mehmet@example.com', 'Mehmet', 'Kaya', 300),
             ('can@example.com', 'Can', 'Demir', 0), ('zeynep@example.com', 'Zeynep', 'Şahin', 80),
             ('pasif@example.com', 'Pasif', 'Hesap', 0)) AS d (email, first_name, last_name, points)
  ON d.email = a.email
WHERE a.account_type = 'CUSTOMER'
ON CONFLICT (account_id) DO NOTHING;

INSERT INTO customer_addresses (id, customer_id, label, city, line, created_at, updated_at, version)
SELECT nextval('customer_address_id_seq'), a.id, d.label, d.city, d.line, now(), now(), 0
FROM accounts a
JOIN (VALUES ('Ev', 'Ankara', 'Kızılay Mah. Atatürk Bulvarı No: 1'),
             ('İş', 'Ankara', 'Çankaya Cad. No: 42')) AS d (label, city, line) ON true
WHERE a.account_type = 'CUSTOMER' AND a.email = 'ayse@example.com'
  AND NOT EXISTS (SELECT 1 FROM customer_addresses x WHERE x.customer_id = a.id AND x.label = d.label);

-- ---------------------------------------------------------------------------------------- membership
INSERT INTO memberships (account_id, tier, starts_at, ends_at, created_at, updated_at, version)
SELECT a.id, d.tier, d.starts_at, d.ends_at, now(), now(), 0
FROM accounts a
JOIN (VALUES ('ayse@example.com', 'GOLD', TIMESTAMPTZ '2026-01-01T00:00:00Z', NULL::TIMESTAMPTZ),
             ('mehmet@example.com', 'SILVER', TIMESTAMPTZ '2026-01-01T00:00:00Z', NULL::TIMESTAMPTZ),
             -- GOLD membership that already ended: must grant nothing
             ('zeynep@example.com', 'GOLD', TIMESTAMPTZ '2025-01-01T00:00:00Z', TIMESTAMPTZ '2026-01-31T00:00:00Z'))
  AS d (email, tier, starts_at, ends_at) ON d.email = a.email
WHERE a.account_type = 'CUSTOMER'
ON CONFLICT (account_id) DO NOTHING;

-- --------------------------------------------------------------------------------------------- staff
INSERT INTO admin_profiles (account_id, full_name, department, created_at, updated_at, version)
SELECT a.id, d.full_name, d.department, now(), now(), 0
FROM accounts a
JOIN (VALUES ('admin@example.com', 'Ali Yönetici', 'Müşteri Hizmetleri'),
             ('super@example.com', 'Selin Süper', 'Bilgi İşlem'),
             ('destek@example.com', 'Deniz Destek', 'Destek')) AS d (email, full_name, department)
  ON d.email = a.email
WHERE a.account_type = 'ADMIN'
ON CONFLICT (account_id) DO NOTHING;

INSERT INTO admin_profile_regions (account_id, region)
SELECT a.id, d.region
FROM accounts a
JOIN (VALUES ('admin@example.com', 'İç Anadolu'), ('admin@example.com', 'Karadeniz'),
             ('super@example.com', 'Tümü')) AS d (email, region) ON d.email = a.email
WHERE a.account_type = 'ADMIN'
ON CONFLICT DO NOTHING;

-- ------------------------------------------------------------------------------------------- content
INSERT INTO articles (id, title, summary, body, required_tier, published_at, created_at, updated_at, version)
VALUES
    (1, 'Üyelere Hoş Geldiniz', 'Bronz ve üstü üyelere açık içerik.',
     'Bu içerik BRONZE ve üzerindeki üyeler içindir. RoleHierarchy sayesinde SILVER ve GOLD üyeler de okur.',
     'BRONZE', '2026-03-01T09:00:00Z', now(), now(), 0),
    (2, 'Gümüş Rehber', 'SILVER ve üstü üyelere açık içerik.',
     'Bu içerik SILVER ve GOLD üyeler içindir. BRONZE üye 403 alır.',
     'SILVER', '2026-04-01T09:00:00Z', now(), now(), 0),
    (3, 'Altın Analiz', 'Sadece GOLD üyelere açık içerik.',
     'Bu içerik yalnızca GOLD üyeler içindir.',
     'GOLD', '2026-05-01T09:00:00Z', now(), now(), 0)
ON CONFLICT (id) DO NOTHING;

SELECT setval('article_id_seq', GREATEST((SELECT max(id) FROM articles), 1));
