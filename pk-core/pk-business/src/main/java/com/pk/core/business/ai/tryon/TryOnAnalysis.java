package com.pk.core.business.ai.tryon;

import java.util.List;

/** Kết quả vision: vùng đeo được + phong cách gợi ý. */
public record TryOnAnalysis(
        List<String> regions,
        List<String> styles,
        String rejectReason
) {
    public boolean usable() {
        return rejectReason == null
                && regions != null
                && regions.stream().anyMatch(TryOnBodyRegion::supported);
    }
}
