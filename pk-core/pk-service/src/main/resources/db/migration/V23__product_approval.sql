-- V23: quy trình duyệt sản phẩm (gửi duyệt / duyệt / từ chối / huỷ). Mặc định hệ thống vẫn cho publish trực tiếp
-- (app.pim.require-approval=false); bật cờ này thì publish trực tiếp bị chặn, phải qua duyệt.
-- Mỗi sản phẩm tối đa 1 yêu cầu PENDING; sản phẩm đang PENDING bị khoá sửa/đổi trạng thái.
CREATE SEQUENCE product_approval_requests_id_seq;
CREATE TABLE product_approval_requests (
    id              BIGINT PRIMARY KEY DEFAULT nextval('product_approval_requests_id_seq'),
    product_id      BIGINT NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    action_type     VARCHAR(20) NOT NULL,
    status          VARCHAR(12) NOT NULL DEFAULT 'PENDING',
    submitted_by    BIGINT,
    submitted_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_by     BIGINT,
    reviewed_at     TIMESTAMPTZ,
    rejected_reason VARCHAR(500),
    before_snapshot TEXT,
    after_snapshot  TEXT,
    created_id      BIGINT,
    created_date    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_id      BIGINT,
    updated_date    TIMESTAMPTZ,
    CONSTRAINT ck_par_action CHECK (action_type IN ('CREATE_PRODUCT', 'UPDATE_PRODUCT')),
    CONSTRAINT ck_par_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'))
);
CREATE INDEX ix_par_product ON product_approval_requests (product_id, id);
CREATE INDEX ix_par_status ON product_approval_requests (status);
CREATE UNIQUE INDEX uq_par_one_pending ON product_approval_requests (product_id) WHERE status = 'PENDING';
ALTER SEQUENCE product_approval_requests_id_seq OWNED BY product_approval_requests.id;
