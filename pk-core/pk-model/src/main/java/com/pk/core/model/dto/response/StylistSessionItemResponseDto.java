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
public class StylistSessionItemResponseDto {

    private Long productId;
    private Long skuId;
    private String reason;
    private BigDecimal matchScore;
    private int sortOrder;
}
