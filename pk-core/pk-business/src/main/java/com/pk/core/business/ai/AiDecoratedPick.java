package com.pk.core.business.ai;

/** Món đã ghép lý do xếp hạng với SKU/giá/tồn. */
public record AiDecoratedPick(AiCandidate candidate, String reason, double matchScore) {
}
