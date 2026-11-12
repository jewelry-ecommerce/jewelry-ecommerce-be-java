package com.pk.core.business.service;

import com.pk.core.model.dto.request.StylistFeedbackRequestDto;
import com.pk.core.model.dto.request.StylistRequestDto;
import com.pk.core.model.dto.response.SetResponseDto;
import com.pk.core.model.dto.response.StylistFeedbackResponseDto;
import com.pk.core.model.dto.response.StylistRecommendationListResponseDto;
import com.pk.core.model.dto.response.StylistSessionResponseDto;

import java.util.List;

/** Gợi ý trang sức: Gemini khi có key, không thì luật dự phòng. userId null = khách, không lưu lịch sử. */
public interface AiStylistService {

    StylistRecommendationListResponseDto recommend(StylistRequestDto req, Long userId);

    SetResponseDto buildSet(StylistRequestDto req, Long userId);

    StylistFeedbackResponseDto feedback(StylistFeedbackRequestDto req, Long userId);

    List<StylistSessionResponseDto> history(Long userId);
}
