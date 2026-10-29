-- V37: sổ kho (inventory ledger) cho điều chỉnh tồn kho thủ công (POST /admin/inventory/adjustments).
-- Mỗi dòng ghi cùng transaction với thay đổi product_skus.on_hand: SKU, lý do, số lượng, tồn trước/sau, người làm.
-- on_hand_after = on_hand_before + quantity_delta (CHECK). reserved_after chỉ là ảnh chụp lúc điều chỉnh, KHÔNG phải ràng buộc.
-- (created_id, idem_key) duy nhất: gửi lại cùng Idempotency-Key + cùng nội dung thì không cộng hai lần;
-- idem_key NULL (không gửi khoá) không bị ràng buộc. Chỉ thêm bảng mới, không đụng bảng hiện có.
CREATE SEQUENCE inventory_movements_id_seq;
CREATE TABLE inventory_movements (
    id             BIGINT PRIMARY KEY DEFAULT nextval('inventory_movements_id_seq'),
    sku_id         BIGINT       NOT NULL REFERENCES product_skus (id),
    warehouse_id   VARCHAR(30)  NOT NULL DEFAULT 'MAIN',
    reason         VARCHAR(20)  NOT NULL,
    quantity_delta INTEGER      NOT NULL,
    on_hand_before INTEGER      NOT NULL,
    on_hand_after  INTEGER      NOT NULL,
    reserved_after INTEGER      NOT NULL,
    reference      VARCHAR(100),
    note           VARCHAR(500),
    idem_key       VARCHAR(100),
    request_hash   VARCHAR(64),
    created_id     BIGINT,
    created_date   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_inventory_movements_reason CHECK (reason IN ('RECEIPT', 'DAMAGE', 'CORRECTION')),
    CONSTRAINT ck_inventory_movements_delta CHECK (quantity_delta <> 0),
    CONSTRAINT ck_inventory_movements_after CHECK (CAST(on_hand_after AS BIGINT) = CAST(on_hand_before AS BIGINT) + quantity_delta),
    CONSTRAINT uq_inventory_movements_idem UNIQUE (created_id, idem_key)
);
CREATE INDEX ix_inventory_movements_sku ON inventory_movements (sku_id, created_date DESC, id DESC);
CREATE INDEX ix_inventory_movements_created ON inventory_movements (created_date DESC, id DESC);
ALTER SEQUENCE inventory_movements_id_seq OWNED BY inventory_movements.id;
