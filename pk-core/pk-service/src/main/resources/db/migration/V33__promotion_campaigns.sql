-- V33: chiến dịch khuyến mãi theo wizard (nháp -> schema -> thành phần -> phát hành).
-- promo_drafts: một chiến dịch; promo_draft_components: đối tượng áp dụng gồm TARGET/AUDIENCE/APPLICABILITY (config JSON);
-- promo_draft_schemas: hình thức khuyến mãi (MARKDOWN/FLAT_PRICE/VOUCHER); promo_schema_components: CONDITION/BENEFIT của schema;
-- promo_voucher_codes: mã voucher của schema VOUCHER (tối đa 1 mã/schema theo FE); promo_published: bản đã phát hành + vòng đời.
-- Khi phát hành, mỗi schema được "hiện thực hoá" thành promotions/vouchers của engine cũ (legacy_promotion_id/legacy_voucher_id)
-- để giá hiển thị, giỏ hàng, checkout dùng chung một nguồn.
CREATE SEQUENCE promo_drafts_id_seq;
CREATE TABLE promo_drafts (
    id            BIGINT PRIMARY KEY DEFAULT nextval('promo_drafts_id_seq'),
    name          VARCHAR(200) NOT NULL,
    description   TEXT,
    starts_at     TIMESTAMPTZ NOT NULL,
    ends_at       TIMESTAMPTZ NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'EDITING',
    published_id  BIGINT,
    created_id    BIGINT,
    created_date  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_id    BIGINT,
    updated_date  TIMESTAMPTZ,
    CONSTRAINT ck_promo_drafts_status CHECK (status IN ('EDITING', 'PUBLISHED', 'DISCARDED'))
);
ALTER SEQUENCE promo_drafts_id_seq OWNED BY promo_drafts.id;

CREATE SEQUENCE promo_draft_components_id_seq;
CREATE TABLE promo_draft_components (
    id            BIGINT PRIMARY KEY DEFAULT nextval('promo_draft_components_id_seq'),
    draft_id      BIGINT NOT NULL REFERENCES promo_drafts (id),
    kind          VARCHAR(20) NOT NULL,
    config        TEXT NOT NULL,
    sort_order    INT NOT NULL DEFAULT 0,
    created_id    BIGINT,
    created_date  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_id    BIGINT,
    updated_date  TIMESTAMPTZ,
    CONSTRAINT ck_promo_draft_components_kind CHECK (kind IN ('TARGET', 'AUDIENCE', 'APPLICABILITY'))
);
ALTER SEQUENCE promo_draft_components_id_seq OWNED BY promo_draft_components.id;
CREATE UNIQUE INDEX uq_promo_draft_components ON promo_draft_components (draft_id, kind);

CREATE SEQUENCE promo_draft_schemas_id_seq;
CREATE TABLE promo_draft_schemas (
    id                    BIGINT PRIMARY KEY DEFAULT nextval('promo_draft_schemas_id_seq'),
    draft_id              BIGINT NOT NULL REFERENCES promo_drafts (id),
    sort_order            INT NOT NULL DEFAULT 0,
    name                  VARCHAR(200) NOT NULL,
    description           TEXT,
    scope                 VARCHAR(20) NOT NULL,
    schema_type           VARCHAR(20) NOT NULL,
    cost_sources          VARCHAR(100),
    priority              INT NOT NULL DEFAULT 0,
    status                VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
    condition_mode        VARCHAR(20) NOT NULL DEFAULT 'EXPLICIT',
    schema_starts_at      TIMESTAMPTZ,
    schema_ends_at        TIMESTAMPTZ,
    unpublished_at        TIMESTAMPTZ,
    legacy_promotion_id   BIGINT,
    created_id            BIGINT,
    created_date          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_id            BIGINT,
    updated_date          TIMESTAMPTZ,
    CONSTRAINT ck_promo_schemas_type CHECK (schema_type IN ('MARKDOWN', 'FLAT_PRICE', 'VOUCHER')),
    CONSTRAINT ck_promo_schemas_status CHECK (status IN ('ENABLED', 'DISABLED'))
);
ALTER SEQUENCE promo_draft_schemas_id_seq OWNED BY promo_draft_schemas.id;
CREATE INDEX ix_promo_schemas_draft ON promo_draft_schemas (draft_id);

CREATE SEQUENCE promo_schema_components_id_seq;
CREATE TABLE promo_schema_components (
    id                BIGINT PRIMARY KEY DEFAULT nextval('promo_schema_components_id_seq'),
    schema_id         BIGINT NOT NULL REFERENCES promo_draft_schemas (id),
    kind              VARCHAR(20) NOT NULL,
    config            TEXT NOT NULL,
    authoring_values  TEXT,
    sort_order        INT NOT NULL DEFAULT 0,
    created_id        BIGINT,
    created_date      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_id        BIGINT,
    updated_date      TIMESTAMPTZ,
    CONSTRAINT ck_promo_schema_components_kind CHECK (kind IN ('CONDITION', 'BENEFIT'))
);
ALTER SEQUENCE promo_schema_components_id_seq OWNED BY promo_schema_components.id;
CREATE UNIQUE INDEX uq_promo_schema_components ON promo_schema_components (schema_id, kind);

CREATE SEQUENCE promo_voucher_codes_id_seq;
CREATE TABLE promo_voucher_codes (
    id                  BIGINT PRIMARY KEY DEFAULT nextval('promo_voucher_codes_id_seq'),
    schema_id           BIGINT NOT NULL REFERENCES promo_draft_schemas (id),
    code                VARCHAR(64) NOT NULL,
    normalized_code     VARCHAR(64) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    image_url           VARCHAR(500),
    issuance_starts_at  TIMESTAMPTZ,
    issuance_ends_at    TIMESTAMPTZ,
    usage_starts_at     TIMESTAMPTZ,
    usage_ends_at       TIMESTAMPTZ,
    policy              TEXT,
    legacy_voucher_id   BIGINT,
    created_id          BIGINT,
    created_date        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_id          BIGINT,
    updated_date        TIMESTAMPTZ
);
ALTER SEQUENCE promo_voucher_codes_id_seq OWNED BY promo_voucher_codes.id;
CREATE UNIQUE INDEX uq_promo_voucher_codes_schema ON promo_voucher_codes (schema_id);
CREATE UNIQUE INDEX uq_promo_voucher_codes_code ON promo_voucher_codes (normalized_code);

CREATE SEQUENCE promo_published_id_seq;
CREATE TABLE promo_published (
    id            BIGINT PRIMARY KEY DEFAULT nextval('promo_published_id_seq'),
    draft_id      BIGINT NOT NULL REFERENCES promo_drafts (id),
    name          VARCHAR(200) NOT NULL,
    description   TEXT,
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    starts_at     TIMESTAMPTZ NOT NULL,
    ends_at       TIMESTAMPTZ NOT NULL,
    published_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at      TIMESTAMPTZ,
    created_id    BIGINT,
    created_date  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_id    BIGINT,
    updated_date  TIMESTAMPTZ,
    CONSTRAINT ck_promo_published_status CHECK (status IN ('ACTIVE', 'PAUSED', 'ENDED', 'ARCHIVED'))
);
ALTER SEQUENCE promo_published_id_seq OWNED BY promo_published.id;
CREATE UNIQUE INDEX uq_promo_published_draft ON promo_published (draft_id);
