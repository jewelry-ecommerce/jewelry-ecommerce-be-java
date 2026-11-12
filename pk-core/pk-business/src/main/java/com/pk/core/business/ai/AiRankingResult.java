package com.pk.core.business.ai;

import java.util.List;

/** Kết quả xếp hạng. source AI khi model trả đủ món; FALLBACK thì banner là gợi ý cơ bản. */
public record AiRankingResult(String source, String fallbackReason, String banner, List<AiRankedPick> picks) {

    public static final String SOURCE_AI = "AI";
    public static final String SOURCE_FALLBACK = "FALLBACK";
    public static final String FALLBACK_BANNER = "Gợi ý cơ bản";
}
