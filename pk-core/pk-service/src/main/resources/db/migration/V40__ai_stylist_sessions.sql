-- V40: lịch sử phiên AI Stylist (tối đa 20/user) và feedback thích/không thích.
-- Không lưu địa chỉ, thẻ, đơn hàng. Guest không ghi lịch sử.
CREATE SEQUENCE ai_stylist_sessions_id_seq;
CREATE TABLE ai_stylist_sessions (
    id              BIGINT PRIMARY KEY DEFAULT nextval('ai_stylist_sessions_id_seq'),
    user_id         BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    occasion        VARCHAR(100) NOT NULL,
    styles_json     TEXT,
    budget          NUMERIC(18, 2),
    budget_scope    VARCHAR(20) NOT NULL DEFAULT 'ITEM',
    recipient       VARCHAR(100),
    source          VARCHAR(20) NOT NULL,
    fallback_reason VARCHAR(255),
    created_date    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_ai_stylist_sessions_source CHECK (source IN ('AI', 'FALLBACK')),
    CONSTRAINT ck_ai_stylist_sessions_scope CHECK (budget_scope IN ('ITEM', 'SET'))
);
CREATE INDEX ix_ai_stylist_sessions_user ON ai_stylist_sessions (user_id, created_date DESC, id DESC);
ALTER SEQUENCE ai_stylist_sessions_id_seq OWNED BY ai_stylist_sessions.id;

CREATE SEQUENCE ai_stylist_session_items_id_seq;
CREATE TABLE ai_stylist_session_items (
    id              BIGINT PRIMARY KEY DEFAULT nextval('ai_stylist_session_items_id_seq'),
    session_id      BIGINT NOT NULL REFERENCES ai_stylist_sessions (id) ON DELETE CASCADE,
    product_id      BIGINT NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    sku_id          BIGINT REFERENCES product_skus (id) ON DELETE SET NULL,
    reason          VARCHAR(500),
    match_score     NUMERIC(6, 4),
    sort_order      INT NOT NULL DEFAULT 0,
    CONSTRAINT uq_ai_stylist_session_product UNIQUE (session_id, product_id)
);
CREATE INDEX ix_ai_stylist_session_items_session ON ai_stylist_session_items (session_id, sort_order);
ALTER SEQUENCE ai_stylist_session_items_id_seq OWNED BY ai_stylist_session_items.id;

CREATE SEQUENCE ai_stylist_feedback_id_seq;
CREATE TABLE ai_stylist_feedback (
    id              BIGINT PRIMARY KEY DEFAULT nextval('ai_stylist_feedback_id_seq'),
    session_id      BIGINT NOT NULL REFERENCES ai_stylist_sessions (id) ON DELETE CASCADE,
    user_id         BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    product_id      BIGINT NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    liked           BOOLEAN NOT NULL,
    created_date    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_date    TIMESTAMPTZ,
    CONSTRAINT uq_ai_stylist_feedback UNIQUE (session_id, product_id)
);
CREATE INDEX ix_ai_stylist_feedback_user_product ON ai_stylist_feedback (user_id, product_id, liked);
ALTER SEQUENCE ai_stylist_feedback_id_seq OWNED BY ai_stylist_feedback.id;
