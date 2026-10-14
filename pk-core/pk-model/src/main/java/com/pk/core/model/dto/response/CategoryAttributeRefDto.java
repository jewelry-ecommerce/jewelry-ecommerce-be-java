package com.pk.core.model.dto.response;

import com.pk.core.model.entity.ProductAttributeEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Tóm tắt thuộc tính trong response danh mục (direct / inherited / effective). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CategoryAttributeRefDto {

    private Long id;
    private String code;
    private String name;
    private String type;
    private String displayType;
    private String status;

    public static CategoryAttributeRefDto from(ProductAttributeEntity a) {
        return new CategoryAttributeRefDto(a.getId(), a.getCode(), a.getName(), a.getType(), a.getDisplayType(),
                a.getStatus());
    }
}
