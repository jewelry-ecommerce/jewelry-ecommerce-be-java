package com.pk.core.model.dto.response;

import com.pk.core.model.entity.ProductAttributeValueEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductAttributeValueResponseDto {

    private Long id;
    private String code;
    private String value;
    private String imageUrl;
    private int sortOrder;

    public static ProductAttributeValueResponseDto from(ProductAttributeValueEntity e) {
        return new ProductAttributeValueResponseDto(e.getId(), e.getCode(), e.getValue(), e.getImageUrl(),
                e.getSortOrder());
    }

    /** Alias tên hiển thị cho FE dùng `name`. */
    public String getName() {
        return value;
    }
}
