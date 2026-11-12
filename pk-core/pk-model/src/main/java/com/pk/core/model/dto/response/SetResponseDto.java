package com.pk.core.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/** Bộ sinh tức thời. source/banner cho biết gợi ý từ model hay luật dự phòng. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SetResponseDto {

    private String code;
    private String name;
    private String description;
    private BigDecimal bundleDiscountPercent;
    private List<SetItemResponseDto> items;
    private String source;
    private String banner;
    private BigDecimal totalPrice;
    private Long sessionId;
}
