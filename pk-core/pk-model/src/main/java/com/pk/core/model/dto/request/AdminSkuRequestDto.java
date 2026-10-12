package com.pk.core.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** 1 phần tử trong `variants[]` lúc tạo sản phẩm (Admin Catalog mục J: POST /admin/catalog/products).
 * Không có DTO update riêng vì spec không liệt kê endpoint sửa/thêm variant sau khi sản phẩm đã tạo
 * (chỉ có GET để xem) - xem javadoc AdminCatalogRest. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminSkuRequestDto {

    @NotBlank(message = "Mã SKU không được để trống")
    private String skuCode;

    private String name;
    private String material;
    private String gemstone;
    private String sizeLabel;
    private String metalColorLabel;
    private BigDecimal caratWeight;
    private BigDecimal weightGram;

    @NotNull(message = "Giá niêm yết không được để trống")
    @PositiveOrZero(message = "Giá niêm yết không được âm")
    private BigDecimal listPrice;

    private BigDecimal salePrice;
    /** Giá vốn (admin-only), >= 0, null = chưa có. */
    @PositiveOrZero(message = "Giá vốn không được âm")
    private BigDecimal costPrice;
    /** Giá bán sỉ B2B (admin-only), >= 0, null = chưa có. */
    @PositiveOrZero(message = "Giá sỉ không được âm")
    private BigDecimal b2bWholesalePrice;
    /** Thuế suất riêng (%) 0..100, null = theo sản phẩm/mặc định. */
    @jakarta.validation.constraints.DecimalMin("0")
    @jakarta.validation.constraints.DecimalMax("100")
    private BigDecimal taxRateOverride;
    @PositiveOrZero
    private BigDecimal length;
    @PositiveOrZero
    private BigDecimal width;
    @PositiveOrZero
    private BigDecimal height;

    @PositiveOrZero(message = "Tồn kho không được âm")
    private int onHand;

    private boolean isDefault;
    /** Chỉ dùng khi thêm/sửa SKU sau khi tạo sản phẩm: DRAFT | PUBLISHED | ARCHIVED (bỏ trống = giữ nguyên / DRAFT). */
    private String status;
}
