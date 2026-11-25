-- V43: role ROOT + phân quyền trang theo user (xem/sửa/xóa). Chỉ ROOT quản lý grant.
INSERT INTO roles (name)
SELECT 'ROOT'
 WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ROOT');

CREATE SEQUENCE user_page_permissions_id_seq;
CREATE TABLE user_page_permissions (
    id           BIGINT PRIMARY KEY DEFAULT nextval('user_page_permissions_id_seq'),
    user_id      BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    page_code    VARCHAR(80) NOT NULL,
    can_view     BOOLEAN NOT NULL DEFAULT FALSE,
    can_edit     BOOLEAN NOT NULL DEFAULT FALSE,
    can_delete   BOOLEAN NOT NULL DEFAULT FALSE,
    created_id   BIGINT,
    created_date TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_id   BIGINT,
    updated_date TIMESTAMPTZ,
    CONSTRAINT uq_user_page_permissions UNIQUE (user_id, page_code)
);
CREATE INDEX ix_user_page_permissions_user ON user_page_permissions (user_id);
ALTER SEQUENCE user_page_permissions_id_seq OWNED BY user_page_permissions.id;

-- Gán ROOT cho mọi tài khoản đang có ADMIN (demo đồ án thường 1 admin).
INSERT INTO user_roles (user_id, role_id)
SELECT ur.user_id, root.id
  FROM user_roles ur
  JOIN roles admin ON admin.id = ur.role_id AND admin.name = 'ADMIN'
  JOIN roles root ON root.name = 'ROOT'
 WHERE NOT EXISTS (
       SELECT 1 FROM user_roles x WHERE x.user_id = ur.user_id AND x.role_id = root.id
 );
