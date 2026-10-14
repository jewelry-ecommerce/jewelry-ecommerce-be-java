package com.pk.core.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** Tạo/sửa danh mục (Admin Catalog mục J: POST/PUT /admin/catalog/categories). `slug` bỏ trống thì
 * tự sinh từ `name`. `code` bỏ trống khi tạo thì lấy theo slug; khi sửa null = giữ nguyên.
 * `status` ACTIVE|INACTIVE (null = ACTIVE khi tạo, giữ nguyên khi sửa). `directAttributeIds`: thuộc tính gán
 * trực tiếp (null khi sửa = giữ nguyên, [] = bỏ hết); thuộc tính kế thừa từ cha không được gửi lại. */
@Getter
@Setter
@NoArgsConstructor
public class AdminCategoryRequestDto {

    @NotBlank(message = "Tên danh mục không được để trống")
    private String name;

    private String slug;
    private String description;
    private String imageUrl;
    private Long parentId;
    private int sortOrder;
    private String code;
    private String status;
    private List<Long> directAttributeIds;

    public AdminCategoryRequestDto(String name, String slug, String description, String imageUrl, Long parentId,
                                   int sortOrder) {
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.imageUrl = imageUrl;
        this.parentId = parentId;
        this.sortOrder = sortOrder;
    }
}
