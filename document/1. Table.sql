-- =====================================================================================
-- pk-core / KLTN - SCRIPT TẠO BẢNG (DDL thuần, chạy tay bằng psql/pgAdmin/DBeaver)
-- =====================================================================================
-- Gộp lại từ 8 migration Flyway THẬT của project (KHÔNG sửa 8 file gốc đó):
--   pk-service/src/main/resources/db/migration/V1__init.sql
--   pk-service/src/main/resources/db/migration/V2__admin_extensions.sql
--   pk-service/src/main/resources/db/migration/V3__users_login_by_phone.sql
--   pk-service/src/main/resources/db/migration/V4__phone_otps.sql
--   pk-service/src/main/resources/db/migration/V5__cart_wishlist.sql
--   pk-service/src/main/resources/db/migration/V6__banners.sql
--   pk-service/src/main/resources/db/migration/V7__customer_requests.sql
--   pk-service/src/main/resources/db/migration/V8__pre_order_configs.sql
-- (V3: đăng nhập bằng SĐT - users.phone NOT NULL UNIQUE; users.email thành cột phụ tuỳ chọn)
-- Dùng file này khi muốn tạo schema trực tiếp (không qua Flyway) - đúng tình huống hiện tại
-- của project: DB dev (kltn_ecommerce) đang tạo bảng bằng tay vì spring.flyway.enabled=false
-- (xem application-dev.yml). Toàn bộ 30 bảng, đúng thứ tự phụ thuộc khóa ngoại (FK) để chạy
-- 1 lượt không lỗi. Không chứa dữ liệu mẫu - xem file "2. Data.sql" cho phần đó.
--
-- Chạy: psql -U postgres -d kltn_ecommerce -f "1. Table.sql"
-- =====================================================================================

BEGIN;

-- ===================== SEQUENCE =====================
-- Mỗi bảng có 1 sequence <bảng>_id_seq; cột id lấy giá trị từ nextval() của nó
-- (khớp @PrimaryKey(generationType = SEQUENCE, generator = "<bảng>_id_seq") bên Java).
CREATE SEQUENCE roles_id_seq;
CREATE SEQUENCE users_id_seq;
CREATE SEQUENCE refresh_tokens_id_seq;
CREATE SEQUENCE customer_addresses_id_seq;
CREATE SEQUENCE categories_id_seq;
CREATE SEQUENCE collections_id_seq;
CREATE SEQUENCE products_id_seq;
CREATE SEQUENCE product_skus_id_seq;
CREATE SEQUENCE product_attributes_id_seq;
CREATE SEQUENCE orders_id_seq;
CREATE SEQUENCE order_items_id_seq;
CREATE SEQUENCE order_timeline_id_seq;
CREATE SEQUENCE payments_id_seq;
CREATE SEQUENCE vouchers_id_seq;
CREATE SEQUENCE promotions_id_seq;
CREATE SEQUENCE cms_pages_id_seq;
CREATE SEQUENCE cms_blocks_id_seq;
CREATE SEQUENCE badge_templates_id_seq;
CREATE SEQUENCE badge_flow_id_seq;
CREATE SEQUENCE phone_otps_id_seq;
CREATE SEQUENCE carts_id_seq;
CREATE SEQUENCE cart_items_id_seq;
CREATE SEQUENCE wishlist_items_id_seq;
CREATE SEQUENCE banner_placements_id_seq;
CREATE SEQUENCE banners_id_seq;
CREATE SEQUENCE customer_requests_id_seq;
CREATE SEQUENCE pre_order_configs_id_seq;

-- ===================== IDENTITY =====================
CREATE TABLE roles (
    id            BIGINT PRIMARY KEY DEFAULT nextval('roles_id_seq'),
    name          VARCHAR(30) NOT NULL,
    created_id    BIGINT,
    created_date  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_roles_name UNIQUE (name)
);

CREATE TABLE users (
    id             BIGINT PRIMARY KEY DEFAULT nextval('users_id_seq'),
    phone          VARCHAR(20) NOT NULL,
    email          VARCHAR(254),
    password_hash  VARCHAR(100) NOT NULL,
    full_name      VARCHAR(120) NOT NULL,
    enabled        BOOLEAN NOT NULL DEFAULT TRUE,
    created_id     BIGINT,
    created_date   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_id     BIGINT,
    updated_date   TIMESTAMPTZ,
    deleted_id     BIGINT,
    deleted_date   TIMESTAMPTZ,
    CONSTRAINT uq_users_phone UNIQUE (phone),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_phone_format CHECK (phone ~ '^0[0-9]{9}$'),
    CONSTRAINT ck_users_email_lowercase CHECK (email = lower(email))
);

CREATE TABLE user_roles (
    user_id  BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id  BIGINT NOT NULL REFERENCES roles (id),
    PRIMARY KEY (user_id, role_id)
);
CREATE INDEX ix_user_roles_role ON user_roles (role_id);

CREATE TABLE refresh_tokens (
    id            BIGINT PRIMARY KEY DEFAULT nextval('refresh_tokens_id_seq'),
    user_id       BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash    VARCHAR(64) NOT NULL,
    family_id     VARCHAR(36) NOT NULL,
    expires_at    TIMESTAMPTZ NOT NULL,
    revoked_at    TIMESTAMPTZ,
    created_id    BIGINT,
    created_date  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_id    BIGINT,
    updated_date  TIMESTAMPTZ,
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
);
CREATE INDEX ix_refresh_tokens_user    ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_family  ON refresh_tokens (family_id);
CREATE INDEX ix_refresh_tokens_expires ON refresh_tokens (expires_at);

-- ===================== ĐỊA CHỈ GIAO HÀNG =====================
CREATE TABLE customer_addresses (
    id              BIGINT PRIMARY KEY DEFAULT nextval('customer_addresses_id_seq'),
    user_id         BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    recipient_name  VARCHAR(120) NOT NULL,
    phone           VARCHAR(20) NOT NULL,
    province        VARCHAR(100) NOT NULL,
    district        VARCHAR(100) NOT NULL,
    ward            VARCHAR(100) NOT NULL,
    address_line    VARCHAR(255) NOT NULL,
    is_default      BOOLEAN NOT NULL DEFAULT FALSE,
    created_id      BIGINT,
    created_date    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_id      BIGINT,
    updated_date    TIMESTAMPTZ,
    deleted_id      BIGINT,
    deleted_date    TIMESTAMPTZ
);
CREATE INDEX ix_customer_addresses_user ON customer_addresses (user_id);

-- ===================== CATALOG =====================
CREATE TABLE categories (
    id            BIGINT PRIMARY KEY DEFAULT nextval('categories_id_seq'),
    parent_id     BIGINT REFERENCES categories (id),
    name          VARCHAR(120) NOT NULL,
    slug          VARCHAR(140) NOT NULL,
    description   VARCHAR(500),
    image_url     VARCHAR(500),
    sort_order    INTEGER NOT NULL DEFAULT 0,
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_id    BIGINT,
    created_date  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_id    BIGINT,
    updated_date  TIMESTAMPTZ,
    deleted_id    BIGINT,
    deleted_date  TIMESTAMPTZ,
    CONSTRAINT uq_categories_slug UNIQUE (slug)
);
CREATE INDEX ix_categories_parent ON categories (parent_id);

CREATE TABLE collections (
    id              BIGINT PRIMARY KEY DEFAULT nextval('collections_id_seq'),
    name            VARCHAR(150) NOT NULL,
    slug            VARCHAR(170) NOT NULL,
    description     VARCHAR(1000),
    hero_image_url  VARCHAR(500),
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_id      BIGINT,
    created_date    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_id      BIGINT,
    updated_date    TIMESTAMPTZ,
    deleted_id      BIGINT,
    deleted_date    TIMESTAMPTZ,
    CONSTRAINT uq_collections_slug UNIQUE (slug),
    CONSTRAINT ck_collections_status CHECK (status IN ('DRAFT', 'PUBLISHED'))
);

CREATE TABLE products (
    id                 BIGINT PRIMARY KEY DEFAULT nextval('products_id_seq'),
    category_id        BIGINT NOT NULL REFERENCES categories (id),
    code               VARCHAR(60) NOT NULL,
    name               VARCHAR(200) NOT NULL,
    slug               VARCHAR(220) NOT NULL,
    short_description  VARCHAR(300),
    description        VARCHAR(2000),
    material           VARCHAR(80),
    occasion           VARCHAR(80),
    thumbnail_url      VARCHAR(500),
    base_price         NUMERIC(15, 0) NOT NULL DEFAULT 0,
    status             VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    published_at       TIMESTAMPTZ,
    version            BIGINT NOT NULL DEFAULT 0,
    created_id         BIGINT,
    created_date       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_id         BIGINT,
    updated_date       TIMESTAMPTZ,
    deleted_id         BIGINT,
    deleted_date       TIMESTAMPTZ,
    CONSTRAINT uq_products_code UNIQUE (code),
    CONSTRAINT uq_products_slug UNIQUE (slug),
    CONSTRAINT ck_products_price CHECK (base_price >= 0),
    CONSTRAINT ck_products_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT ck_products_published CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL)
);
CREATE INDEX ix_products_category ON products (category_id);
CREATE INDEX ix_products_published ON products (published_at DESC) WHERE status = 'PUBLISHED';

CREATE TABLE product_collections (
    product_id     BIGINT NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    collection_id  BIGINT NOT NULL REFERENCES collections (id) ON DELETE CASCADE,
    PRIMARY KEY (product_id, collection_id)
);
CREATE INDEX ix_product_collections_collection ON product_collections (collection_id);

-- Biến thể (SKU) - tồn kho theo SKU: available = on_hand - reserved.
CREATE TABLE product_skus (
    id                 BIGINT PRIMARY KEY DEFAULT nextval('product_skus_id_seq'),
    product_id         BIGINT NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    sku_code           VARCHAR(64) NOT NULL,
    name               VARCHAR(150),
    status             VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    material           VARCHAR(80),
    gemstone           VARCHAR(80),
    size_label         VARCHAR(40),
    metal_color_label  VARCHAR(40),
    carat_weight       NUMERIC(6, 2),
    weight_gram        NUMERIC(8, 2),
    list_price         NUMERIC(15, 0) NOT NULL,
    sale_price         NUMERIC(15, 0),
    on_hand            INTEGER NOT NULL DEFAULT 0,
    reserved           INTEGER NOT NULL DEFAULT 0,
    is_default         BOOLEAN NOT NULL DEFAULT FALSE,
    version            BIGINT NOT NULL DEFAULT 0,
    created_id         BIGINT,
    created_date       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_id         BIGINT,
    updated_date       TIMESTAMPTZ,
    deleted_id         BIGINT,
    deleted_date       TIMESTAMPTZ,
    CONSTRAINT uq_product_skus_code UNIQUE (sku_code),
    CONSTRAINT ck_product_skus_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT ck_product_skus_list_price CHECK (list_price >= 0),
    CONSTRAINT ck_product_skus_sale_price CHECK (sale_price IS NULL OR sale_price >= 0),
    CONSTRAINT ck_product_skus_on_hand CHECK (on_hand >= 0),
    CONSTRAINT ck_product_skus_reserved CHECK (reserved >= 0)
    -- Không ràng buộc reserved <= on_hand: đơn đặt trước (pre-order) cho phép giữ chỗ vượt tồn kho.
);
CREATE INDEX ix_product_skus_product ON product_skus (product_id);

-- ===================== THUỘC TÍNH SẢN PHẨM (Admin Catalog) =====================
CREATE TABLE product_attributes (
    id            BIGINT PRIMARY KEY DEFAULT nextval('product_attributes_id_seq'),
    name          VARCHAR(120) NOT NULL,
    code          VARCHAR(60) NOT NULL,
    type          VARCHAR(20) NOT NULL,
    options       VARCHAR(2000),
    created_id    BIGINT,
    created_date  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_id    BIGINT,
    updated_date  TIMESTAMPTZ,
    CONSTRAINT uq_product_attributes_code UNIQUE (code),
    CONSTRAINT ck_product_attributes_type CHECK (type IN ('TEXT', 'SELECT', 'MULTISELECT'))
);

-- ===================== ĐƠN HÀNG =====================
CREATE TABLE orders (
    id                   BIGINT PRIMARY KEY DEFAULT nextval('orders_id_seq'),
    code                 VARCHAR(30) NOT NULL,
    user_id              BIGINT NOT NULL REFERENCES users (id),
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    payment_status       VARCHAR(20) NOT NULL DEFAULT 'UNPAID',
    payment_method       VARCHAR(30) NOT NULL,
    shipping_method      VARCHAR(30) NOT NULL,
    tracking_code        VARCHAR(60),
    ship_recipient_name  VARCHAR(120) NOT NULL,
    ship_phone           VARCHAR(20) NOT NULL,
    ship_province        VARCHAR(100) NOT NULL,
    ship_district        VARCHAR(100) NOT NULL,
    ship_ward            VARCHAR(100) NOT NULL,
    ship_address_line    VARCHAR(255) NOT NULL,
    subtotal             NUMERIC(15, 0) NOT NULL,
    product_discount     NUMERIC(15, 0) NOT NULL DEFAULT 0,
    voucher_discount     NUMERIC(15, 0) NOT NULL DEFAULT 0,
    shipping_fee         NUMERIC(15, 0) NOT NULL DEFAULT 0,
    grand_total          NUMERIC(15, 0) NOT NULL,
    applied_voucher_code VARCHAR(40),
    note                 VARCHAR(500),
    placed_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_id           BIGINT,
    created_date         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_id           BIGINT,
    updated_date         TIMESTAMPTZ,
    CONSTRAINT uq_orders_code UNIQUE (code),
    CONSTRAINT ck_orders_status CHECK (status IN ('PENDING', 'CONFIRMED', 'SHIPPING', 'DELIVERED', 'CANCELLED', 'RETURNED')),
    CONSTRAINT ck_orders_payment_status CHECK (payment_status IN ('UNPAID', 'PAID', 'REFUNDED')),
    CONSTRAINT ck_orders_grand_total CHECK (grand_total >= 0)
);
CREATE INDEX ix_orders_user ON orders (user_id);
CREATE INDEX ix_orders_status ON orders (status);

CREATE TABLE order_items (
    id            BIGINT PRIMARY KEY DEFAULT nextval('order_items_id_seq'),
    order_id      BIGINT NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id    BIGINT NOT NULL REFERENCES products (id),
    sku_id        BIGINT NOT NULL REFERENCES product_skus (id),
    name          VARCHAR(200) NOT NULL,
    image_url     VARCHAR(500),
    quantity      INTEGER NOT NULL,
    unit_price    NUMERIC(15, 0) NOT NULL,
    line_total    NUMERIC(15, 0) NOT NULL,
    created_id    BIGINT,
    created_date  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_items_unit_price CHECK (unit_price >= 0)
);
CREATE INDEX ix_order_items_order ON order_items (order_id);

CREATE TABLE order_timeline (
    id            BIGINT PRIMARY KEY DEFAULT nextval('order_timeline_id_seq'),
    order_id      BIGINT NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    status        VARCHAR(20) NOT NULL,
    occurred_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    note          VARCHAR(500),
    actor         VARCHAR(60),
    created_id    BIGINT,
    created_date  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_order_timeline_order ON order_timeline (order_id);

-- ===================== THANH TOÁN =====================
CREATE TABLE payments (
    id              BIGINT PRIMARY KEY DEFAULT nextval('payments_id_seq'),
    order_id        BIGINT NOT NULL REFERENCES orders (id),
    provider        VARCHAR(30) NOT NULL,
    transaction_id  VARCHAR(100),
    amount          NUMERIC(15, 0) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    signature       VARCHAR(500),
    created_id      BIGINT,
    created_date    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_id      BIGINT,
    updated_date    TIMESTAMPTZ,
    CONSTRAINT ck_payments_amount CHECK (amount >= 0),
    CONSTRAINT ck_payments_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED'))
);
CREATE INDEX ix_payments_order ON payments (order_id);

-- ===================== VOUCHER & KHUYẾN MÃI =====================
CREATE TABLE vouchers (
    id                   BIGINT PRIMARY KEY DEFAULT nextval('vouchers_id_seq'),
    code                 VARCHAR(40) NOT NULL,
    discount_type        VARCHAR(20) NOT NULL,
    discount_value       NUMERIC(15, 0) NOT NULL,
    max_discount_amount  NUMERIC(15, 0),
    min_order_value      NUMERIC(15, 0) NOT NULL DEFAULT 0,
    usage_limit          INTEGER,
    used_count           INTEGER NOT NULL DEFAULT 0,
    starts_at            TIMESTAMPTZ NOT NULL,
    ends_at              TIMESTAMPTZ NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_id           BIGINT,
    created_date         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_id           BIGINT,
    updated_date         TIMESTAMPTZ,
    deleted_id           BIGINT,
    deleted_date         TIMESTAMPTZ,
    CONSTRAINT uq_vouchers_code UNIQUE (code),
    CONSTRAINT ck_vouchers_discount_type CHECK (discount_type IN ('PERCENT', 'FIXED')),
    CONSTRAINT ck_vouchers_status CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT ck_vouchers_period CHECK (ends_at > starts_at)
);

CREATE TABLE promotions (
    id                   BIGINT PRIMARY KEY DEFAULT nextval('promotions_id_seq'),
    name                 VARCHAR(150) NOT NULL,
    discount_type        VARCHAR(20) NOT NULL,
    discount_value       NUMERIC(15, 0) NOT NULL,
    max_discount_amount  NUMERIC(15, 0),
    starts_at            TIMESTAMPTZ NOT NULL,
    ends_at              TIMESTAMPTZ NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_id           BIGINT,
    created_date         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_id           BIGINT,
    updated_date         TIMESTAMPTZ,
    deleted_id           BIGINT,
    deleted_date         TIMESTAMPTZ,
    CONSTRAINT ck_promotions_discount_type CHECK (discount_type IN ('PERCENT', 'FIXED', 'FLAT_PRICE')),
    CONSTRAINT ck_promotions_status CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT ck_promotions_period CHECK (ends_at > starts_at)
);

CREATE TABLE promotion_products (
    promotion_id  BIGINT NOT NULL REFERENCES promotions (id) ON DELETE CASCADE,
    product_id    BIGINT NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    PRIMARY KEY (promotion_id, product_id)
);

-- ===================== CMS =====================
CREATE TABLE cms_pages (
    id            BIGINT PRIMARY KEY DEFAULT nextval('cms_pages_id_seq'),
    slug          VARCHAR(140) NOT NULL,
    title         VARCHAR(200) NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_id    BIGINT,
    created_date  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_id    BIGINT,
    updated_date  TIMESTAMPTZ,
    deleted_id    BIGINT,
    deleted_date  TIMESTAMPTZ,
    CONSTRAINT uq_cms_pages_slug UNIQUE (slug),
    CONSTRAINT ck_cms_pages_status CHECK (status IN ('DRAFT', 'PUBLISHED'))
);

CREATE TABLE cms_blocks (
    id            BIGINT PRIMARY KEY DEFAULT nextval('cms_blocks_id_seq'),
    page_id       BIGINT NOT NULL REFERENCES cms_pages (id) ON DELETE CASCADE,
    type            VARCHAR(40) NOT NULL,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    data            TEXT,
    target_segment  VARCHAR(60),
    created_id      BIGINT,
    created_date    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_id      BIGINT,
    updated_date    TIMESTAMPTZ,
    CONSTRAINT ck_cms_blocks_type CHECK (type IN ('BANNER', 'PRODUCT_CAROUSEL', 'PRODUCT_LIST', 'INFO_CARDS', 'IMAGE_GALLERY', 'PRODUCT_CATEGORY_NAV', 'PRODUCT_COLLECTION_SHOWCASE', 'PRODUCT_EXPANDABLE_DESCRIPTION'))
);
CREATE INDEX ix_cms_blocks_page ON cms_blocks (page_id);

-- ===================== BADGE =====================
-- Theo spec FE (nhóm "Badge - Nhãn dán"). style_config/asset_meta/rule_config/templates là JSON dạng TEXT.
-- badge_flow.templates = mảng [{badgeTemplateId, priorityWeight, isPinned}]. Xoá mẫu nhãn là xoá MỀM.
CREATE TABLE badge_templates (
    id                       BIGINT PRIMARY KEY DEFAULT nextval('badge_templates_id_seq'),
    name                     VARCHAR(120) NOT NULL,
    code                     VARCHAR(80) NOT NULL,
    description              VARCHAR(500),
    type                     VARCHAR(20) NOT NULL,
    badge_type               VARCHAR(20) NOT NULL,
    status                   VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    display_text             VARCHAR(60),
    default_position         VARCHAR(20) NOT NULL DEFAULT 'TOP_LEFT',
    style_config             TEXT,
    icon                     VARCHAR(255),
    image                    VARCHAR(500),
    icon_mobile              VARCHAR(255),
    image_mobile             VARCHAR(500),
    asset_meta               TEXT,
    default_priority_weight  INTEGER NOT NULL DEFAULT 0,
    created_id               BIGINT,
    created_date             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_id               BIGINT,
    updated_date             TIMESTAMPTZ,
    deleted_id               BIGINT,
    deleted_date             TIMESTAMPTZ,
    CONSTRAINT uq_badge_templates_code UNIQUE (code),
    CONSTRAINT ck_badge_templates_type CHECK (type IN ('TEXT', 'ICON', 'IMAGE', 'MINI_BANNER')),
    CONSTRAINT ck_badge_templates_badge_type CHECK (badge_type IN ('CAMPAIGN', 'BEST_SELLER', 'PRICE_DIFF', 'OUT_OF_STOCK', 'NEW_ARRIVAL', 'LOW_STOCK', 'PRE_ORDER')),
    CONSTRAINT ck_badge_templates_status CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_badge_templates_position CHECK (default_position IN ('TOP_LEFT', 'TOP_CENTER', 'TOP_RIGHT', 'CENTER_LEFT', 'CENTER_RIGHT', 'BOTTOM_LEFT', 'BOTTOM_CENTER', 'BOTTOM_RIGHT', 'PRICE_LINE'))
);

CREATE TABLE badge_flow (
    id            BIGINT PRIMARY KEY DEFAULT nextval('badge_flow_id_seq'),
    name          VARCHAR(150) NOT NULL,
    description   VARCHAR(500),
    status        VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    active_from   TIMESTAMPTZ,
    active_to     TIMESTAMPTZ,
    rule_type     VARCHAR(20) NOT NULL,
    rule_config   TEXT,
    channel       VARCHAR(20) NOT NULL DEFAULT 'ALL',
    templates     TEXT NOT NULL,
    created_id    BIGINT,
    created_date  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_id    BIGINT,
    updated_date  TIMESTAMPTZ,
    CONSTRAINT ck_badge_flow_status CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_badge_flow_rule_type CHECK (rule_type IN ('MANUAL', 'ALL', 'CATEGORY', 'COLLECTION', 'PROMOTION', 'OUT_OF_STOCK')),
    CONSTRAINT ck_badge_flow_channel CHECK (channel IN ('ALL', 'WEB', 'MOBILE_WEB', 'APP'))
);

-- ===================== OTP SỐ ĐIỆN THOẠI =====================
-- Mã OTP (đăng ký / quên mật khẩu) và registration token: chỉ lưu SHA-256, không lưu bản rõ.
CREATE TABLE phone_otps (
    id                       BIGINT PRIMARY KEY DEFAULT nextval('phone_otps_id_seq'),
    phone                    VARCHAR(20) NOT NULL,
    purpose                  VARCHAR(20) NOT NULL,
    code_hash                VARCHAR(64) NOT NULL,
    expires_at               TIMESTAMPTZ NOT NULL,
    attempts                 INT NOT NULL DEFAULT 0,
    verified_at              TIMESTAMPTZ,
    registration_token_hash  VARCHAR(64),
    registration_expires_at  TIMESTAMPTZ,
    consumed_at              TIMESTAMPTZ,
    created_id               BIGINT,
    created_date             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_id               BIGINT,
    updated_date             TIMESTAMPTZ,
    CONSTRAINT ck_phone_otps_purpose CHECK (purpose IN ('REGISTER', 'RESET_PASSWORD')),
    CONSTRAINT ck_phone_otps_phone_format CHECK (phone ~ '^0[0-9]{9}$')
);
CREATE INDEX ix_phone_otps_phone ON phone_otps (phone, purpose, created_date DESC);
CREATE UNIQUE INDEX uq_phone_otps_reg_token ON phone_otps (registration_token_hash) WHERE registration_token_hash IS NOT NULL;

-- ===================== GIỎ HÀNG & YÊU THÍCH =====================
-- carts: 1 giỏ cho mỗi khách đăng nhập (user_id) HOẶC 1 giỏ cho khách vãng lai (guest_id, UUID do server cấp).
CREATE TABLE carts (
    id            BIGINT PRIMARY KEY DEFAULT nextval('carts_id_seq'),
    user_id       BIGINT REFERENCES users (id) ON DELETE CASCADE,
    guest_id      VARCHAR(64),
    created_id    BIGINT,
    created_date  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_id    BIGINT,
    updated_date  TIMESTAMPTZ,
    CONSTRAINT uq_carts_user UNIQUE (user_id),
    CONSTRAINT uq_carts_guest UNIQUE (guest_id),
    CONSTRAINT ck_carts_owner CHECK ((user_id IS NOT NULL AND guest_id IS NULL) OR (user_id IS NULL AND guest_id IS NOT NULL))
);

CREATE TABLE cart_items (
    id            BIGINT PRIMARY KEY DEFAULT nextval('cart_items_id_seq'),
    cart_id       BIGINT NOT NULL REFERENCES carts (id) ON DELETE CASCADE,
    sku_id        BIGINT NOT NULL REFERENCES product_skus (id) ON DELETE CASCADE,
    quantity      INT NOT NULL,
    created_id    BIGINT,
    created_date  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_id    BIGINT,
    updated_date  TIMESTAMPTZ,
    CONSTRAINT uq_cart_items_cart_sku UNIQUE (cart_id, sku_id),
    CONSTRAINT ck_cart_items_quantity CHECK (quantity >= 1)
);

CREATE TABLE wishlist_items (
    id            BIGINT PRIMARY KEY DEFAULT nextval('wishlist_items_id_seq'),
    user_id       BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    product_id    BIGINT NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    created_id    BIGINT,
    created_date  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_wishlist_items_user_product UNIQUE (user_id, product_id)
);
CREATE INDEX ix_wishlist_items_user ON wishlist_items (user_id, created_date DESC);

-- ===================== BANNER =====================
-- banner_placements: vị trí hiển thị (seed ở "2. Data.sql"); banners.actions là JSON dạng TEXT; placement_code/sort_order là mở rộng ngoài spec.
CREATE TABLE banner_placements (
    id            BIGINT PRIMARY KEY DEFAULT nextval('banner_placements_id_seq'),
    code          VARCHAR(60) NOT NULL,
    name          VARCHAR(150) NOT NULL,
    display_type  VARCHAR(30) NOT NULL DEFAULT 'CAROUSEL',
    created_id    BIGINT,
    created_date  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_banner_placements_code UNIQUE (code)
);

CREATE TABLE banners (
    id                       BIGINT PRIMARY KEY DEFAULT nextval('banners_id_seq'),
    internal_name            VARCHAR(150) NOT NULL,
    media_url                VARCHAR(500),
    media_mobile_url         VARCHAR(500),
    media_link_url           VARCHAR(500),
    media_poster_url         VARCHAR(500),
    media_mobile_poster_url  VARCHAR(500),
    media_fit                VARCHAR(20) NOT NULL DEFAULT 'cover',
    media_type               VARCHAR(20) NOT NULL DEFAULT 'IMAGE',
    layout                   VARCHAR(20) NOT NULL DEFAULT 'CENTER',
    title                    VARCHAR(200),
    subtitle                 VARCHAR(300),
    title_color              VARCHAR(30),
    actions_layout           VARCHAR(20) NOT NULL DEFAULT 'STACK',
    actions                  TEXT,
    overlay_opacity          NUMERIC(3, 2) NOT NULL DEFAULT 0,
    status                   VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    placement_code           VARCHAR(60),
    sort_order               INTEGER NOT NULL DEFAULT 0,
    created_id               BIGINT,
    created_date             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_id               BIGINT,
    updated_date             TIMESTAMPTZ,
    deleted_id               BIGINT,
    deleted_date             TIMESTAMPTZ,
    CONSTRAINT ck_banners_status CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_banners_overlay CHECK (overlay_opacity >= 0 AND overlay_opacity <= 1)
);
CREATE INDEX ix_banners_placement ON banners (placement_code, status, sort_order);

-- ===================== YÊU CẦU KHÁCH HÀNG =====================
-- Một bảng chung cho BACK_IN_STOCK / ORDER_SUPPORT / NEWSLETTER (xem V7__customer_requests.sql). Đặt SAU products/product_skus/orders.
CREATE TABLE customer_requests (
    id                          BIGINT PRIMARY KEY DEFAULT nextval('customer_requests_id_seq'),
    type                        VARCHAR(20) NOT NULL,
    contact_channel             VARCHAR(10) NOT NULL,
    contact_value               VARCHAR(255) NOT NULL,
    status                      VARCHAR(20) NOT NULL DEFAULT 'NEW',
    product_id                  BIGINT REFERENCES products (id) ON DELETE SET NULL,
    sku_id                      BIGINT REFERENCES product_skus (id) ON DELETE SET NULL,
    sku_code                    VARCHAR(64),
    product_name_snapshot       VARCHAR(200),
    product_image_url_snapshot  VARCHAR(500),
    variant_text_snapshot       VARCHAR(300),
    order_id                    BIGINT REFERENCES orders (id) ON DELETE SET NULL,
    order_code                  VARCHAR(60),
    order_type                  VARCHAR(20),
    created_id                  BIGINT,
    created_date                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_id                  BIGINT,
    updated_date                TIMESTAMPTZ,
    CONSTRAINT ck_customer_requests_type CHECK (type IN ('BACK_IN_STOCK', 'ORDER_SUPPORT', 'NEWSLETTER')),
    CONSTRAINT ck_customer_requests_channel CHECK (contact_channel IN ('PHONE', 'EMAIL')),
    CONSTRAINT ck_customer_requests_status CHECK (status IN ('NEW', 'UNPROCESSED', 'CONTACTED', 'IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT ck_customer_requests_order_type CHECK (order_type IS NULL OR order_type IN ('ORDER', 'PRE_ORDER'))
);
CREATE INDEX ix_customer_requests_type ON customer_requests (type, status, created_date DESC);

-- ===================== CẤU HÌNH ĐẶT TRƯỚC =====================
-- Lịch sử bật/tắt đặt trước, dòng mới nhất là cấu hình hiệu lực (xem V8__pre_order_configs.sql).
CREATE TABLE pre_order_configs (
    id            BIGINT PRIMARY KEY DEFAULT nextval('pre_order_configs_id_seq'),
    enabled       BOOLEAN NOT NULL,
    message       VARCHAR(500),
    created_id    BIGINT,
    created_date  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Gắn sequence vào cột id: DROP TABLE thì sequence bị xoá theo.
ALTER SEQUENCE roles_id_seq OWNED BY roles.id;
ALTER SEQUENCE users_id_seq OWNED BY users.id;
ALTER SEQUENCE refresh_tokens_id_seq OWNED BY refresh_tokens.id;
ALTER SEQUENCE customer_addresses_id_seq OWNED BY customer_addresses.id;
ALTER SEQUENCE categories_id_seq OWNED BY categories.id;
ALTER SEQUENCE collections_id_seq OWNED BY collections.id;
ALTER SEQUENCE products_id_seq OWNED BY products.id;
ALTER SEQUENCE product_skus_id_seq OWNED BY product_skus.id;
ALTER SEQUENCE product_attributes_id_seq OWNED BY product_attributes.id;
ALTER SEQUENCE orders_id_seq OWNED BY orders.id;
ALTER SEQUENCE order_items_id_seq OWNED BY order_items.id;
ALTER SEQUENCE order_timeline_id_seq OWNED BY order_timeline.id;
ALTER SEQUENCE payments_id_seq OWNED BY payments.id;
ALTER SEQUENCE vouchers_id_seq OWNED BY vouchers.id;
ALTER SEQUENCE promotions_id_seq OWNED BY promotions.id;
ALTER SEQUENCE cms_pages_id_seq OWNED BY cms_pages.id;
ALTER SEQUENCE cms_blocks_id_seq OWNED BY cms_blocks.id;
ALTER SEQUENCE badge_templates_id_seq OWNED BY badge_templates.id;
ALTER SEQUENCE badge_flow_id_seq OWNED BY badge_flow.id;
ALTER SEQUENCE phone_otps_id_seq OWNED BY phone_otps.id;
ALTER SEQUENCE carts_id_seq OWNED BY carts.id;
ALTER SEQUENCE cart_items_id_seq OWNED BY cart_items.id;
ALTER SEQUENCE wishlist_items_id_seq OWNED BY wishlist_items.id;
ALTER SEQUENCE banner_placements_id_seq OWNED BY banner_placements.id;
ALTER SEQUENCE banners_id_seq OWNED BY banners.id;
ALTER SEQUENCE customer_requests_id_seq OWNED BY customer_requests.id;
ALTER SEQUENCE pre_order_configs_id_seq OWNED BY pre_order_configs.id;

COMMIT;
