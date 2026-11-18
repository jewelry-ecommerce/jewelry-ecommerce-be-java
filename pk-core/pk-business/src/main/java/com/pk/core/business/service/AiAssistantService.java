package com.pk.core.business.service;

import com.pk.core.model.dto.request.AssistantHistoryItemDto;
import com.pk.core.model.dto.response.AssistantMessageResponseDto;

import java.util.List;

/** Trả lời câu chat của khách. Không lọc ngân sách mặc định. */
public interface AiAssistantService {

    AssistantMessageResponseDto chat(String message, List<AssistantHistoryItemDto> history);
}
