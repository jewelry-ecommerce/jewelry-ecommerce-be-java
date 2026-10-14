package com.pk.core.model.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
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

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;
    private String title;
    private String description;
    private int sortOrder;

    public static ProductInfoResponseDto from(ProductInfoEntity e) {
        return new ProductInfoResponseDto(e.getId(), e.getTitle(), e.getDescription(), e.getSortOrder());
    }
}
