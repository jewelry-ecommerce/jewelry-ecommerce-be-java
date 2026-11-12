package com.pk.core.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Một món gợi ý. source = AI hoặc FALLBACK (cùng nguồn của cả danh sách). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StylistRecommendationResponseDto {

    private String type;
    private Long id;
    private String name;
    private String slug;
    private String thumbnailUrl;
    private String reason;
    private double matchScore;
    private Long skuId;
    private String skuCode;
    private BigDecimal price;
    private Integer stock;
    private String source;
}
