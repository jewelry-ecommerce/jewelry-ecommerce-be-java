package com.pk.core.model.dto.response;

import com.pk.core.common.dto.UpdateDto;
import com.pk.core.model.entity.ProductAttributeEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** extends UpdateDto (không phải BaseDto) - khớp đúng mức kế thừa của {@link ProductAttributeEntity}
 * (extends UpdateEntity, bảng product_attributes không có cột deleted_id/deleted_date). Không có
 * static `of()` sẵn cho mức UpdateDto (chỉ BaseDto có) nên gọi tay `copyAudit()`, giống
 * OrderResponseDto. */
@Getter
@Setter
@NoArgsConstructor
public class ProductAttributeResponseDto extends UpdateDto {

    private Long id;
    private String name;
    private String code;
    private String type;
    private List<String> options;
    /** ACTIVE | INACTIVE. */
    private String status;
    /** TEXT | IMAGE. */
    private String displayType;
    /** Giá trị có id/code/ảnh (options chỉ là tên các giá trị, giữ cho FE cũ). */
    private List<ProductAttributeValueResponseDto> values = new java.util.ArrayList<>();

    public static ProductAttributeResponseDto from(ProductAttributeEntity e) {
        ProductAttributeResponseDto dto = new ProductAttributeResponseDto();
        dto.setId(e.getId());
        dto.setName(e.getName());
        dto.setCode(e.getCode());
        dto.setType(e.getType());
        dto.setOptions(e.getOptionsList());
        dto.setStatus(e.getStatus());
        dto.setDisplayType(e.getDisplayType());
        dto.copyAudit(e);
        return dto;
    }

    public static ProductAttributeResponseDto from(ProductAttributeEntity e,
                                                   List<com.pk.core.model.entity.ProductAttributeValueEntity> valueRows) {
        ProductAttributeResponseDto dto = from(e);
        dto.setValues(valueRows.stream().map(ProductAttributeValueResponseDto::from).toList());
        if (!valueRows.isEmpty()) {
            dto.setOptions(valueRows.stream().map(com.pk.core.model.entity.ProductAttributeValueEntity::getValue).toList());
        }
        return dto;
    }
}
