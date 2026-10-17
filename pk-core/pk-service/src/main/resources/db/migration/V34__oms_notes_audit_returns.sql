-- V34: OMS nâng cao - ghi chú đơn, audit log, yêu cầu đổi/trả. Không sửa migration cũ.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS source VARCHAR(30) NOT NULL DEFAULT 'WEBSITE';

CREATE SEQUENCE order_notes_id_seq;
CREATE TABLE order_notes (
    id            BIGINT PRIMARY KEY DEFAULT nextval('order_notes_id_seq'),
    order_id      BIGINT NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    type          VARCHAR(20) NOT NULL,
    content       VARCHAR(2000) NOT NULL,
    author_id     BIGINT,
    author_type   VARCHAR(30),
    author_name   VARCHAR(150),
    created_id    BIGINT,
    created_date  TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER SEQUENCE order_notes_id_seq OWNED BY order_notes.id;
CREATE INDEX ix_order_notes_order_id ON order_notes (order_id);

CREATE SEQUENCE order_audit_logs_id_seq;
CREATE TABLE order_audit_logs (
    id            BIGINT PRIMARY KEY DEFAULT nextval('order_audit_logs_id_seq'),
    order_id      BIGINT NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    entity_name   VARCHAR(30) NOT NULL DEFAULT 'oms',
    action        VARCHAR(60) NOT NULL,
    action_type   VARCHAR(30),
    source        VARCHAR(30),
    message       VARCHAR(500),
    note          VARCHAR(1000),
    actor_id      BIGINT,
    actor_name    VARCHAR(150),
    actor_role    VARCHAR(60),
    changes       TEXT,
    created_id    BIGINT,
    created_date  TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER SEQUENCE order_audit_logs_id_seq OWNED BY order_audit_logs.id;
CREATE INDEX ix_order_audit_logs_order_id ON order_audit_logs (order_id);

CREATE SEQUENCE order_returns_id_seq;
CREATE TABLE order_returns (
    id            BIGINT PRIMARY KEY DEFAULT nextval('order_returns_id_seq'),
    code          VARCHAR(40) NOT NULL,
    order_id      BIGINT NOT NULL REFERENCES orders (id),
    type          VARCHAR(20) NOT NULL,
    reason        VARCHAR(40) NOT NULL,
    reason_category VARCHAR(20),
    status        VARCHAR(20) NOT NULL DEFAULT 'Pending',
    note          VARCHAR(1000),
    return_total  NUMERIC(15, 0) NOT NULL DEFAULT 0,
    exchange_total NUMERIC(15, 0) NOT NULL DEFAULT 0,
    price_difference NUMERIC(15, 0) NOT NULL DEFAULT 0,
    total_refund_amount NUMERIC(15, 0) NOT NULL DEFAULT 0,
    return_fault_party VARCHAR(20),
    payment_method VARCHAR(30),
    tracking_code VARCHAR(60),
    evidence_urls TEXT,
    shipping_address TEXT,
    refund_bank_holder VARCHAR(120),
    refund_bank_number VARCHAR(60),
    refund_bank_name VARCHAR(120),
    refund_bank_branch VARCHAR(120),
    exchange_order_id BIGINT,
    resolved_by_id BIGINT,
    resolved_by_name VARCHAR(150),
    resolved_at   TIMESTAMPTZ,
    resolved_note VARCHAR(1000),
    reject_reason VARCHAR(1000),
    source        VARCHAR(30) NOT NULL DEFAULT 'ADMIN',
    created_id    BIGINT,
    created_date  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_id    BIGINT,
    updated_date  TIMESTAMPTZ
);
ALTER SEQUENCE order_returns_id_seq OWNED BY order_returns.id;
CREATE INDEX ix_order_returns_order_id ON order_returns (order_id);
CREATE UNIQUE INDEX uq_order_returns_code ON order_returns (code);
CREATE INDEX ix_order_returns_order ON order_returns (order_id);

CREATE SEQUENCE order_return_items_id_seq;
CREATE TABLE order_return_items (
    id            BIGINT PRIMARY KEY DEFAULT nextval('order_return_items_id_seq'),
    return_id     BIGINT NOT NULL REFERENCES order_returns (id) ON DELETE CASCADE,
    kind          VARCHAR(10) NOT NULL,
    order_item_id BIGINT,
    sku_id        BIGINT NOT NULL,
    product_id    BIGINT,
    product_name  VARCHAR(200),
    variation_name VARCHAR(200),
    sku_code      VARCHAR(60),
    image_url     VARCHAR(500),
    unit_price    NUMERIC(15, 0) NOT NULL DEFAULT 0,
    quantity      INTEGER NOT NULL,
    line_total    NUMERIC(15, 0) NOT NULL DEFAULT 0,
    created_id    BIGINT,
    created_date  TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER SEQUENCE order_return_items_id_seq OWNED BY order_return_items.id;
CREATE INDEX ix_order_return_items_return_id ON order_return_items (return_id);

CREATE SEQUENCE order_return_status_history_id_seq;
CREATE TABLE order_return_status_history (
    id            BIGINT PRIMARY KEY DEFAULT nextval('order_return_status_history_id_seq'),
    return_id     BIGINT NOT NULL REFERENCES order_returns (id) ON DELETE CASCADE,
    from_status   VARCHAR(20),
    to_status     VARCHAR(20) NOT NULL,
    changed_by_id BIGINT,
    changed_by_type VARCHAR(20),
    note          VARCHAR(1000),
    created_id    BIGINT,
    created_date  TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER SEQUENCE order_return_status_history_id_seq OWNED BY order_return_status_history.id;
CREATE INDEX ix_order_return_status_history_return_id ON order_return_status_history (return_id);

