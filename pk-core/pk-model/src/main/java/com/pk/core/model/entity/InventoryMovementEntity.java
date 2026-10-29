package com.pk.core.model.entity;

import com.pk.core.common.entity.CreateEntity;
import com.pk.core.model.constant.TableConstant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import vn.com.unit.miragesql.miragesql.annotation.Column;
import vn.com.unit.miragesql.miragesql.annotation.PrimaryKey;
import vn.com.unit.miragesql.miragesql.annotation.PrimaryKey.GenerationType;
import vn.com.unit.miragesql.miragesql.annotation.Table;

/**
 * Một dòng sổ kho (V37): một lần điều chỉnh tồn kho thủ công. Ghi cùng transaction với product_skus.on_hand.
 * createdId = admin thực hiện, createdDate = thời điểm. (createdId, idemKey) duy nhất khi idemKey có giá trị.
 */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.INVENTORY_MOVEMENTS)
public class InventoryMovementEntity extends CreateEntity {

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "inventory_movements_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "sku_id")
    private Long skuId;

    @Column(name = "warehouse_id")
    private String warehouseId;

    @Column(name = "reason")
    private String reason;

    @Column(name = "quantity_delta")
    private int quantityDelta;

    @Column(name = "on_hand_before")
    private int onHandBefore;

    @Column(name = "on_hand_after")
    private int onHandAfter;

    @Column(name = "reserved_after")
    private int reservedAfter;

    @Column(name = "reference")
    private String reference;

    @Column(name = "note")
    private String note;

    @Column(name = "idem_key")
    private String idemKey;

    @Column(name = "request_hash")
    private String requestHash;
}
