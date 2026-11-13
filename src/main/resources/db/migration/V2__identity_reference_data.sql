-- Reference data needed in every environment: the default role assigned at registration.

INSERT INTO roles (id, name, created_at, updated_at, version)
VALUES (nextval('role_id_seq'), 'USER', now(), now(), 0);

INSERT INTO role_permissions (role_id, permission_name)
SELECT id, permission FROM roles, (VALUES ('profile:read'), ('profile:write')) AS p (permission)
WHERE name = 'USER';
