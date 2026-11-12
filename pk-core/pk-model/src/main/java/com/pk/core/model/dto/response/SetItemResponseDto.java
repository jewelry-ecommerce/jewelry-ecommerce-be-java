package com.pk.core.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SetItemResponseDto {

    private Long productId;
    private String productName;
    private String slug;
    private String thumbnailUrl;
    private String slotName;
    private boolean isKeyPiece;
    private int sortOrder;
    private Long skuId;
    private String skuCode;
    private BigDecimal price;
    private Integer stock;
    private String source;
}
