package com.pk.core.model.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** Tạo sản phẩm + SKU (Admin Catalog mục J: POST /admin/catalog/products). `slug` bỏ trống thì tự
 * sinh từ `name`. Phải có ít nhất 1 variant (không cho tạo sản phẩm rỗng SKU - storefront cần SKU để
 * tính priceFrom/tồn kho). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminCreateProductRequestDto extends AdminProductExtraRequestDto {

    @NotNull(message = "Danh mục không được để trống")
    private Long categoryId;

    @NotBlank(message = "Mã sản phẩm không được để trống")
    private String code;

    @NotBlank(message = "Tên sản phẩm không được để trống")
    private String name;

    private String slug;
    private String shortDescription;
    private String description;
    private String material;
    private String occasion;
    private String thumbnailUrl;

    @NotEmpty(message = "Sản phẩm phải có ít nhất 1 biến thể (SKU)")
    private List<@Valid @NotNull AdminSkuRequestDto> variants;
}
