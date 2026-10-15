-- V25: chống đặt đơn trùng khi client gửi lại (header Idempotency-Key). Khoá hiệu lực 24 giờ.
-- UNIQUE (owner, idem_key) là chốt cuối khi hai request cùng khoá chạy đồng thời.
CREATE SEQUENCE order_idempotency_keys_id_seq;
CREATE TABLE order_idempotency_keys (
    id           BIGINT PRIMARY KEY DEFAULT nextval('order_idempotency_keys_id_seq'),
    owner        VARCHAR(64)  NOT NULL,
    idem_key     VARCHAR(100) NOT NULL,
    request_hash VARCHAR(64)  NOT NULL,
    order_code   VARCHAR(40)  NOT NULL,
    created_id   BIGINT,
    created_date TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_order_idem UNIQUE (owner, idem_key)
);
CREATE INDEX ix_order_idem_created ON order_idempotency_keys (created_date);
ALTER SEQUENCE order_idempotency_keys_id_seq OWNED BY order_idempotency_keys.id;
