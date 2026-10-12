package com.pk.core.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Cập nhật sản phẩm (Admin Catalog mục J: PUT /admin/catalog/products/{productId}) - CHỈ sửa thông
 * tin mô tả sản phẩm, KHÔNG đổi variants[] (xem AdminSkuRequestDto javadoc: spec không có endpoint
 * sửa/thêm SKU sau khi tạo). Không cho đổi `code` (định danh nghiệp vụ, đổi dễ vỡ tham chiếu). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminUpdateProductRequestDto extends AdminProductExtraRequestDto {

    /** Tuỳ chọn: nếu gửi thì phải đúng mã hiện tại (mã sản phẩm không đổi được; khác -> 400 CODE_IMMUTABLE). */
    private String code;

    @NotNull(message = "Danh mục không được để trống")
    private Long categoryId;

    @NotBlank(message = "Tên sản phẩm không được để trống")
    private String name;

    private String shortDescription;
    private String description;
    private String material;
    private String occasion;
    private String thumbnailUrl;
}
