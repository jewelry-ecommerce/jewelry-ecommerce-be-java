package com.pk.core.model.dto.response;

import com.pk.core.model.entity.ProductInfoEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductInfoResponseDto {

    private String title;
    private String description;
    private int sortOrder;

    public static ProductInfoResponseDto from(ProductInfoEntity e) {
        return new ProductInfoResponseDto(e.getTitle(), e.getDescription(), e.getSortOrder());
    }
}
