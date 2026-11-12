package com.pk.core.business.ai;

import com.pk.core.model.dto.request.StylistRequestDto;

import java.math.BigDecimal;
import java.util.Locale;

/** Khoảng ngân sách ± dung sai. upper/lower null = không giới hạn. */
public final class AiBudgetBand {

    public static final String ITEM = "ITEM";
    public static final String SET = "SET";

    private final BigDecimal lower;
    private final BigDecimal upper;

    private AiBudgetBand(BigDecimal lower, BigDecimal upper) {
        this.lower = lower;
        this.upper = upper;
    }

    static AiBudgetBand of(BigDecimal budget, BigDecimal ratio) {
        if (budget == null) {
            return new AiBudgetBand(null, null);
        }
        BigDecimal safeRatio = ratio == null ? new BigDecimal("0.10") : ratio;
        BigDecimal low = budget.multiply(BigDecimal.ONE.subtract(safeRatio)).max(BigDecimal.ZERO);
        BigDecimal high = budget.multiply(BigDecimal.ONE.add(safeRatio));
        return new AiBudgetBand(low, high);
    }

    boolean fits(BigDecimal price, boolean looseUpper) {
        if (price == null || price.signum() < 0) {
            return false;
        }
        if (upper == null) {
            return true;
        }
        if (price.compareTo(upper) > 0) {
            return false;
        }
        if (looseUpper) {
            return price.signum() > 0;
        }
        return lower == null || price.compareTo(lower) >= 0;
    }

    BigDecimal upper() {
        return upper == null ? new BigDecimal("999999999999") : upper;
    }

    public static String scope(StylistRequestDto request) {
        if (request.getBudgetScope() == null || request.getBudgetScope().isBlank()) {
            return ITEM;
        }
        return request.getBudgetScope().trim().toUpperCase(Locale.ROOT);
    }
}
