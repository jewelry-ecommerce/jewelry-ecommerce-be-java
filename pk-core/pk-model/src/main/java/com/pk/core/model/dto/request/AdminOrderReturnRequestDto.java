package com.pk.core.model.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** Body cho POST /admin/orders/{orderCode}/return (mục L spec). `lineIds` là id các OrderItemEntity
 * (KHÔNG phải skuId) được xác nhận hoàn trả - chỉ áp dụng khi đơn đang DELIVERED (xem
 * OrderEntity.isReturnable()). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminOrderReturnRequestDto {

    /** Cách cũ: trả TOÀN BỘ số lượng còn lại của các dòng này. Dùng `lines` nếu cần trả một phần số lượng. */
    private List<Long> lineIds;

    /** Trả theo số lượng từng dòng: [{lineId, quantity}]. */
    @jakarta.validation.Valid
    private List<Line> lines;

    @lombok.Getter
    @lombok.Setter
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class Line {
        @jakarta.validation.constraints.NotNull
        private Long lineId;
        @jakarta.validation.constraints.Min(1)
        private int quantity;
    }
}
