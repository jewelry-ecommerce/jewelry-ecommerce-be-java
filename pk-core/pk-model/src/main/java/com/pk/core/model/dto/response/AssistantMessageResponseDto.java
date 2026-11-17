package com.pk.core.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** Câu trả lời của trợ lý, kèm món trong catalog nếu câu hỏi cần gợi ý. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AssistantMessageResponseDto {

    private String reply;
    private List<StylistRecommendationResponseDto> recommendations;

    /** Chỉ có khi model không trả lời được. Không hiện trên khung chat. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String providerStatus;
}
