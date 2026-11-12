package com.pk.core.business.ai;

import com.pk.core.model.dto.request.StylistRequestDto;

import java.util.List;

/** Xếp hạng ứng viên. Ném runtime exception khi không gọi được — orchestrator sẽ đổi sang luật. */
public interface AiRankingProvider {

    AiRankingResult rank(StylistRequestDto request, List<AiCandidate> candidates, boolean setMode);
}
