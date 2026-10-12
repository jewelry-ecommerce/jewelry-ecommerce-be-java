package com.pk.core.model.dto.response;

import com.pk.core.model.entity.OrderItemEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** DTO con nằm trong OrderResponseDto.items - KHÔNG kế thừa BaseDto/CreateDto (không cần lộ
 * createdDate của từng dòng hàng ra ngoài, spec FE cũng không có trường này ở `lines[]`). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponseDto {

    private Long id;
    private Long productId;
    private Long skuId;
    private String name;
    private String imageUrl;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;
    /** Số lượng đã trả; còn trả được = quantity - returnedQuantity. */
    private int returnedQuantity;

    public static OrderItemResponseDto from(OrderItemEntity i) {
        return new OrderItemResponseDto(i.getId(), i.getProductId(), i.getSkuId(), i.getName(), i.getImageUrl(),
                i.getQuantity(), i.getUnitPrice(), i.getLineTotal(), i.getReturnedQuantity());
    }

    public OrderItemResponseDto(Long id, Long productId, Long skuId, String name, String imageUrl, int quantity,
                                BigDecimal unitPrice, BigDecimal lineTotal) {
        this(id, productId, skuId, name, imageUrl, quantity, unitPrice, lineTotal, 0);
    }

    public int getReturnableQuantity() {
        return Math.max(quantity - returnedQuantity, 0);
    }

    // ---- FE OrderDetailItem: field tính toán, giữ nguyên field cũ ----

    public String getVariationId() {
        return skuId == null ? null : String.valueOf(skuId);
    }

    public String getVariationName() {
        return name;
    }

    public String getProductName() {
        return name;
    }

    public String getImage() {
        return imageUrl;
    }

    public String getSalePrice() {
        return unitPrice == null ? null : String.valueOf(unitPrice.longValue());
    }

    public String getDiscountAmount() {
        return "0";
    }

    public String getFinalAmount() {
        return lineTotal == null ? null : String.valueOf(lineTotal.longValue());
    }
}
