package com.pk.core.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Body cho POST /admin/inventory/adjustments (mục M spec). `warehouseId` KHÔNG bắt buộc và bị bỏ
 * qua (decorative) - hệ thống hiện chỉ có 1 kho ngầm định "MAIN", CHƯA hỗ trợ đa kho thật (xem
 * javadoc InventoryService + RULE-CODE.md mục Inventory). `reason` phải là 1 trong RECEIPT/DAMAGE/
 * CORRECTION (validate ở service, 400 VALIDATION_FAILED nếu sai). `quantityDelta` khác 0 và phải đúng dấu theo
 * reason: RECEIPT > 0, DAMAGE < 0, CORRECTION tăng hoặc giảm (sai -> 400 STOCK_ADJUSTMENT_INVALID, field
 * quantityDelta). Từ chối 422: NEGATIVE_STOCK (on_hand sau < 0), STOCK_OVERFLOW (vượt INT), STOCK_BELOW_RESERVED
 * (giảm xuống dưới phần đang giữ chỗ cho đơn). reserved không bao giờ bị sửa từ form này. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminStockAdjustmentRequestDto {

    @NotNull(message = "skuId không được để trống")
    private Long skuId;

    @NotNull(message = "quantityDelta không được để trống")
    private Integer quantityDelta;

    @NotBlank(message = "reason không được để trống")
    private String reason;

    private String warehouseId;

    /** Mã tham chiếu tuỳ chọn ghi vào sổ kho (vd số phiếu nhập, mã lô). */
    @Size(max = 100, message = "reference tối đa 100 ký tự")
    private String reference;

    /** Ghi chú tuỳ chọn ghi vào sổ kho. */
    @Size(max = 500, message = "note tối đa 500 ký tự")
    private String note;
}
