package com.pk.core.business.service;

import com.pk.core.model.dto.response.AssistantMessageResponseDto;

/** Trả lời câu chat của khách. Không lọc ngân sách mặc định. */
public interface AiAssistantService {

    AssistantMessageResponseDto chat(String message);
}
