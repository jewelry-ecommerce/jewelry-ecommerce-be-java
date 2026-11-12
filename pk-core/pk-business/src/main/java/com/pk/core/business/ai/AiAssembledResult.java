package com.pk.core.business.ai;

import java.math.BigDecimal;
import java.util.List;

public record AiAssembledResult(
        String source,
        String banner,
        String fallbackReason,
        List<AiDecoratedPick> picks,
        BigDecimal totalPrice
) {
}
