package com.pk.core.model.dto.response;

import com.pk.core.model.entity.InventoryMovementEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

/** Một dòng sổ kho: lần điều chỉnh tồn thủ công (RECEIPT/DAMAGE/CORRECTION) cho 1 SKU ở kho MAIN. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InventoryMovementResponseDto {

    private Long id;
    private Long skuId;
    private String skuCode;
    private String skuName;
    private String warehouseId;
    private String reason;
    private int quantityDelta;
    private int onHandBefore;
    private int onHandAfter;
    /** Ảnh chụp reserved lúc điều chỉnh (không phải giá trị hiện tại). */
    private int reservedAfter;
    private String reference;
    private String note;
    /** Id admin thực hiện. */
    private Long actorId;
    private Date createdDate;

    public static InventoryMovementResponseDto from(InventoryMovementEntity e, String skuCode, String skuName) {
        return new InventoryMovementResponseDto(e.getId(), e.getSkuId(), skuCode, skuName, e.getWarehouseId(),
                e.getReason(), e.getQuantityDelta(), e.getOnHandBefore(), e.getOnHandAfter(), e.getReservedAfter(),
                e.getReference(), e.getNote(), e.getCreatedId(), e.getCreatedDate());
    }
}
