package com.pk.core.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** Bọc danh sách gợi ý. banner = "Gợi ý cơ bản" khi không dùng được model (source FALLBACK). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StylistRecommendationListResponseDto {

    private String source;
    private String banner;
    private Long sessionId;
    private List<StylistRecommendationResponseDto> recommendations;
}
