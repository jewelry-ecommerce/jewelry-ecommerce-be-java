-- V17: banner builder (SLIDER / GRID / DOUBLE_BANNER). Chỉ THÊM, không sửa/xoá dữ liệu cũ.
--   banner_placements: thêm platform / status / settings(JSON: columns, rows, gap, mobileOddFullWidthItem).
--   banner_placement_layouts: một "bố cục" của vị trí (DRAFT/SCHEDULED/ACTIVE/INACTIVE/ARCHIVED, cửa sổ thời gian).
--   banner_placement_layout_items: banner nào nằm ở slot nào (slot_key), thứ tự (priority), toạ độ/span (display_metadata JSON).
--   Gỡ banner khỏi layout chỉ xoá dòng item, KHÔNG xoá banner. Vị trí chưa có layout nào vẫn render theo banners.placement_code như cũ.
ALTER TABLE banner_placements ADD COLUMN platform VARCHAR(10) NOT NULL DEFAULT 'ALL';
ALTER TABLE banner_placements ADD COLUMN status   VARCHAR(10) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE banner_placements ADD COLUMN settings TEXT;
ALTER TABLE banner_placements ADD CONSTRAINT ck_banner_placements_platform CHECK (platform IN ('WEB', 'APP', 'ALL'));
ALTER TABLE banner_placements ADD CONSTRAINT ck_banner_placements_status CHECK (status IN ('ACTIVE', 'INACTIVE'));

CREATE SEQUENCE banner_placement_layouts_id_seq;
CREATE SEQUENCE banner_placement_layout_items_id_seq;

CREATE TABLE banner_placement_layouts (
    id            BIGINT PRIMARY KEY DEFAULT nextval('banner_placement_layouts_id_seq'),
    placement_id  BIGINT NOT NULL REFERENCES banner_placements (id) ON DELETE CASCADE,
    name          VARCHAR(150) NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    active_from   TIMESTAMPTZ,
    active_to     TIMESTAMPTZ,
    note          VARCHAR(500),
    created_id    BIGINT,
    created_date  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_id    BIGINT,
    updated_date  TIMESTAMPTZ,
    CONSTRAINT ck_banner_layouts_status CHECK (status IN ('DRAFT', 'SCHEDULED', 'ACTIVE', 'INACTIVE', 'ARCHIVED'))
);
CREATE INDEX ix_banner_layouts_placement ON banner_placement_layouts (placement_id, status);

CREATE TABLE banner_placement_layout_items (
    id                BIGINT PRIMARY KEY DEFAULT nextval('banner_placement_layout_items_id_seq'),
    layout_id         BIGINT NOT NULL REFERENCES banner_placement_layouts (id) ON DELETE CASCADE,
    slot_key          VARCHAR(60) NOT NULL,
    banner_id         BIGINT NOT NULL REFERENCES banners (id),
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    priority          INTEGER NOT NULL DEFAULT 0,
    display_metadata  TEXT,
    created_id        BIGINT,
    created_date      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_id        BIGINT,
    updated_date      TIMESTAMPTZ,
    CONSTRAINT uq_banner_layout_items UNIQUE (layout_id, slot_key, banner_id)
);
CREATE INDEX ix_banner_layout_items_layout ON banner_placement_layout_items (layout_id, slot_key, priority);

ALTER SEQUENCE banner_placement_layouts_id_seq OWNED BY banner_placement_layouts.id;
ALTER SEQUENCE banner_placement_layout_items_id_seq OWNED BY banner_placement_layout_items.id;
