package com.pk.core.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/** Body chung cho gợi ý stylist và set builder. budgetScope ITEM = ngân sách từng món, SET = cả bộ. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StylistRequestDto {

    @NotBlank
    private String occasion;

    private List<String> style;

    private BigDecimal budget;

    /** ITEM | SET. Bỏ trống = ITEM. */
    private String budgetScope;

    private String recipient;
}
