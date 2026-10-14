package com.pk.core.model.dto.response;

import com.pk.core.model.dto.request.BadgeFlowTemplateRefDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.List;
import java.util.Map;

/** Luồng hiển thị nhãn theo spec FE; `isActive` = ACTIVE và đang trong khoảng activeFrom-activeTo. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BadgeFlowResponseDto {

    private String id;
    private String name;
    private String description;
    private String status;
    private Boolean isActive;
    private Date activeFrom;
    private Date activeTo;
    private String ruleType;
    private Map<String, Object> ruleConfig;
    private String channel;
    private List<BadgeFlowTemplateRefDto> templates;
    /** DRAFT | PAUSED | SCHEDULED | RUNNING | ENDED - tính theo thời điểm thật (khớp BadgeCampaignDisplayStatus của FE). */
    private String displayStatus;
}
