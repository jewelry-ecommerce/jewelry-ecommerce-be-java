-- V26: giao diện chung của site do admin cấu hình: thanh thông báo đầu trang (header), footer (1 dòng duy nhất), logo.
-- Xoá header/logo là xoá mềm (deleted_date). Footer lưu mạng xã hội và cột menu dạng JSON (TEXT) vì luôn đọc/ghi nguyên khối.
CREATE SEQUENCE site_headers_id_seq;
CREATE TABLE site_headers (
    id                 BIGINT PRIMARY KEY DEFAULT nextval('site_headers_id_seq'),
    banner_name        VARCHAR(150) NOT NULL,
    display_style      VARCHAR(10)  NOT NULL DEFAULT 'TEXT',
    content            VARCHAR(1000),
    bg_color           VARCHAR(30),
    text_color         VARCHAR(30),
    target_url         VARCHAR(500),
    animation_style    VARCHAR(30),
    loop_interval_sec  INT,
    start_time         TIMESTAMPTZ  NOT NULL,
    end_time           TIMESTAMPTZ,
    status             VARCHAR(12)  NOT NULL DEFAULT 'DRAFT',
    created_id         BIGINT,
    created_date       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_id         BIGINT,
    updated_date       TIMESTAMPTZ,
    deleted_id         BIGINT,
    deleted_date       TIMESTAMPTZ,
    CONSTRAINT ck_site_headers_style CHECK (display_style IN ('TEXT', 'MEDIA')),
    CONSTRAINT ck_site_headers_status CHECK (status IN ('DRAFT', 'ACTIVE', 'DEACTIVE', 'SCHEDULED'))
);
ALTER SEQUENCE site_headers_id_seq OWNED BY site_headers.id;

CREATE SEQUENCE site_footer_id_seq;
CREATE TABLE site_footer (
    id                       BIGINT PRIMARY KEY DEFAULT nextval('site_footer_id_seq'),
    copyright_text           VARCHAR(500),
    footer_logo_url          VARCHAR(500),
    certification_image_url  VARCHAR(500),
    certification_url        VARCHAR(500),
    social_links             TEXT,
    menu_columns             TEXT,
    created_id               BIGINT,
    created_date             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_id               BIGINT,
    updated_date             TIMESTAMPTZ,
    deleted_id               BIGINT,
    deleted_date             TIMESTAMPTZ
);
ALTER SEQUENCE site_footer_id_seq OWNED BY site_footer.id;

CREATE SEQUENCE site_logos_id_seq;
CREATE TABLE site_logos (
    id               BIGINT PRIMARY KEY DEFAULT nextval('site_logos_id_seq'),
    name             VARCHAR(150) NOT NULL,
    type             VARCHAR(10)  NOT NULL DEFAULT 'HEADER',
    status           VARCHAR(12)  NOT NULL DEFAULT 'DRAFT',
    logo_url         VARCHAR(500) NOT NULL,
    logo_theme       VARCHAR(30),
    logo_target_url  VARCHAR(500),
    created_id       BIGINT,
    created_date     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_id       BIGINT,
    updated_date     TIMESTAMPTZ,
    deleted_id       BIGINT,
    deleted_date     TIMESTAMPTZ,
    CONSTRAINT ck_site_logos_type CHECK (type IN ('HEADER', 'FOOTER', 'AUTH', 'FAVICON')),
    CONSTRAINT ck_site_logos_status CHECK (status IN ('DRAFT', 'ACTIVE', 'DEACTIVE', 'SCHEDULED'))
);
ALTER SEQUENCE site_logos_id_seq OWNED BY site_logos.id;
