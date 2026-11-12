package com.pk.core.business.ai;

/** Một món do provider chọn. productId phải thuộc danh sách ứng viên đã gửi. */
public record AiRankedPick(long productId, String reason, double matchScore) {
}
