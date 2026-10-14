package com.pk.core.model.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** Tạo/sửa thuộc tính sản phẩm (Admin Catalog mục J: POST/PUT /admin/catalog/attributes).
 * `type`: TEXT/SELECT/MULTISELECT. `displayType`: TEXT|IMAGE (null = TEXT khi tạo, giữ nguyên khi sửa).
 * `status`: ACTIVE|INACTIVE (null tương tự). Giá trị: gửi `values` (có id/code/ảnh) HOẶC `options` (chuỗi, cũ);
 * cả hai null khi sửa = giữ nguyên giá trị hiện có. */
@Getter
@Setter
@NoArgsConstructor
public class AdminAttributeRequestDto {

    @NotBlank(message = "Tên thuộc tính không được để trống")
    private String name;

    @NotBlank(message = "Code thuộc tính không được để trống")
    private String code;

    @NotBlank(message = "Loại thuộc tính không được để trống")
    private String type;

    private List<@NotEmpty String> options;

    private String displayType;

    private String status;

    @Valid
    private List<AdminAttributeValueRequestDto> values;

    public AdminAttributeRequestDto(String name, String code, String type, List<String> options) {
        this.name = name;
        this.code = code;
        this.type = type;
        this.options = options;
    }
}
