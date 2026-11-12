package com.pk.core.business.ai;

import com.pk.core.model.dto.request.StylistRequestDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Xếp hạng khi không gọi được model: dịp, phong cách, độ gần ngân sách. */
@Component
public class RuleBasedAiRankingProvider implements AiRankingProvider {

    private final AiProperties properties;

    public RuleBasedAiRankingProvider(AiProperties properties) {
        this.properties = properties;
    }

    @Override
    public AiRankingResult rank(StylistRequestDto request, List<AiCandidate> candidates, boolean setMode) {
        List<AiCandidate> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator.comparingDouble((AiCandidate candidate) -> score(request, candidate)).reversed()
                .thenComparingLong(AiCandidate::productId));
        int limit = setMode ? ordered.size() : Math.max(1, properties.getMaxRecommendations());
        List<AiRankedPick> picks = new ArrayList<>();
        for (AiCandidate candidate : ordered) {
            if (picks.size() >= limit) {
                break;
            }
            picks.add(new AiRankedPick(candidate.productId(), reason(request, candidate), score(request, candidate)));
        }
        return new AiRankingResult(AiRankingResult.SOURCE_FALLBACK, "rule_based",
                AiRankingResult.FALLBACK_BANNER, picks);
    }

    private double score(StylistRequestDto request, AiCandidate candidate) {
        double value = 0.40;
        if (occasionMatch(request, candidate)) {
            value += 0.35;
        }
        if (styleMatch(request, candidate)) {
            value += 0.15;
        }
        value += priceCloseness(request, candidate);
        return Math.min(0.99d, Math.round(value * 1000d) / 1000d);
    }

    private static boolean occasionMatch(StylistRequestDto request, AiCandidate candidate) {
        return request.getOccasion() != null && candidate.occasion() != null
                && request.getOccasion().equalsIgnoreCase(candidate.occasion());
    }

    private static boolean styleMatch(StylistRequestDto request, AiCandidate candidate) {
        if (request.getStyle() == null || request.getStyle().isEmpty()) {
            return false;
        }
        String haystack = ((candidate.name() == null ? "" : candidate.name()) + " "
                + (candidate.material() == null ? "" : candidate.material())).toLowerCase(Locale.ROOT);
        for (String style : request.getStyle()) {
            if (style != null && !style.isBlank() && haystack.contains(style.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static double priceCloseness(StylistRequestDto request, AiCandidate candidate) {
        if (request.getBudget() == null || request.getBudget().signum() <= 0 || candidate.price() == null) {
            return 0.05d;
        }
        BigDecimal gap = candidate.price().subtract(request.getBudget()).abs();
        double ratio = gap.doubleValue() / request.getBudget().doubleValue();
        return Math.max(0d, 0.10d * (1d - Math.min(1d, ratio)));
    }

    private static String reason(StylistRequestDto request, AiCandidate candidate) {
        StringBuilder text = new StringBuilder("Phù hợp dịp ").append(request.getOccasion());
        if (request.getBudget() != null) {
            text.append(", trong ngân sách");
        }
        if (request.getRecipient() != null && !request.getRecipient().isBlank()) {
            text.append(", dành tặng ").append(request.getRecipient().trim());
        }
        return text.toString();
    }
}
