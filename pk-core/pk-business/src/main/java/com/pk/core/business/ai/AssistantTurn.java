package com.pk.core.business.ai;

import java.util.List;

/** Câu trả lời và đúng những sản phẩm tool đã lấy trong lượt này. */
public record AssistantTurn(AssistantReplyParser.Draft draft, List<AiCandidate> products) {
}
