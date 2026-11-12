package com.pk.core.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StylistSessionResponseDto {

    private Long id;
    private String occasion;
    private List<String> styles;
    private BigDecimal budget;
    private String budgetScope;
    private String recipient;
    private String source;
    private String fallbackReason;
    private Date createdDate;
    private List<StylistSessionItemResponseDto> items;
}
