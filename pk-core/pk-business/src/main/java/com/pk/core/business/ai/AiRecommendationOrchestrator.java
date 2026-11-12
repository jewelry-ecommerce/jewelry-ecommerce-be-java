package com.pk.core.business.ai;

import com.pk.core.business.repository.AiStylistFeedbackRepo;
import com.pk.core.business.repository.CategoryRepo;
import com.pk.core.business.repository.ProductRepo;
import com.pk.core.business.repository.ProductSkuRepo;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.model.dto.request.StylistRequestDto;
import com.pk.core.model.entity.CategoryEntity;
import com.pk.core.model.entity.ProductEntity;
import com.pk.core.model.entity.ProductSkuEntity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Lọc ngân sách ± dung sai, gắn SKU, gọi model rồi rơi về luật với banner "Gợi ý cơ bản". */
@Component
public class AiRecommendationOrchestrator {

    private static final BigDecimal OPEN_MAX = new BigDecimal("999999999999");

    private final ProductRepo products;
    private final ProductSkuRepo skus;
    private final CategoryRepo categories;
    private final AiStylistFeedbackRepo feedback;
    private final AiProperties properties;
    private final AiRankingProvider gemini;
    private final RuleBasedAiRankingProvider rules;

    public AiRecommendationOrchestrator(ProductRepo products, ProductSkuRepo skus, CategoryRepo categories,
                                        AiStylistFeedbackRepo feedback, AiProperties properties,
                                        @Qualifier("geminiAiRankingProvider") AiRankingProvider gemini,
                                        RuleBasedAiRankingProvider rules) {
        this.products = products;
        this.skus = skus;
        this.categories = categories;
        this.feedback = feedback;
        this.properties = properties;
        this.gemini = gemini;
        this.rules = rules;
    }

    public AiAssembledResult recommend(StylistRequestDto request, Long userId) {
        List<AiCandidate> pool = loadCandidates(request, userId);
        if (pool.isEmpty()) {
            return new AiAssembledResult(AiRankingResult.SOURCE_FALLBACK, AiRankingResult.FALLBACK_BANNER,
                    "no_match", List.of(), BigDecimal.ZERO);
        }
        AiRankingResult ranked = rank(request, pool, false);
        int limit = Math.max(1, properties.getMaxRecommendations());
        return assembled(ranked, decorate(pool, ranked, limit));
    }

    public AiAssembledResult buildSet(StylistRequestDto request, Long userId) {
        List<AiCandidate> pool = loadCandidates(request, userId);
        if (pool.isEmpty()) {
            throw invalidSet("Không đủ sản phẩm để tạo bộ");
        }
        AiRankingResult ranked = rank(request, pool, true);
        try {
            return finishSet(request, ranked, pool);
        } catch (BusinessException ex) {
            if (!retryWithRules(ranked, ex)) {
                throw ex;
            }
            return finishSet(request, rulesResult(request, pool, true), pool);
        }
    }

    private List<AiCandidate> loadCandidates(StylistRequestDto request, Long userId) {
        List<ProductEntity> found = products.searchPublished("", "", "", "", BigDecimal.ZERO, OPEN_MAX);
        List<AiCandidate> occasioned = preferOccasion(enrich(found), request.getOccasion());
        boolean loose = AiBudgetBand.SET.equals(AiBudgetBand.scope(request));
        AiBudgetBand band = AiBudgetBand.of(request.getBudget(), properties.getBudgetToleranceRatio());
        List<AiCandidate> priced = filterPrice(occasioned, band, loose);
        if (priced.isEmpty() && request.getBudget() != null && !occasioned.isEmpty()) {
            throw budgetExceeded();
        }
        int max = Math.max(1, properties.getMaxCandidates());
        return cap(excludeDisliked(priced, userId), max);
    }

    private List<AiCandidate> enrich(List<ProductEntity> found) {
        List<AiCandidate> out = new ArrayList<>();
        if (found == null) {
            return out;
        }
        for (ProductEntity product : found) {
            AiCandidate candidate = toCandidate(product);
            if (candidate != null) {
                out.add(candidate);
            }
        }
        return out;
    }

    private AiCandidate toCandidate(ProductEntity product) {
        if (product == null || product.getId() == null) {
            return null;
        }
        ProductSkuEntity sku = representativeSku(product.getId());
        if (sku == null || sku.effectivePrice() == null) {
            return null;
        }
        CategoryEntity category = product.getCategoryId() == null ? null : categories.findOne(product.getCategoryId());
        String categoryName = category == null || category.getName() == null ? "Khác" : category.getName();
        return new AiCandidate(product.getId(), product.getCategoryId(), categoryName, product.getName(),
                product.getSlug(), product.getThumbnailUrl(), product.getOccasion(), product.getMaterial(),
                sku.getId(), sku.getSkuCode(), sku.effectivePrice(), sku.available());
    }

    private ProductSkuEntity representativeSku(Long productId) {
        List<ProductSkuEntity> rows = skus.findByProductId(productId);
        if (rows == null) {
            return null;
        }
        ProductSkuEntity first = null;
        for (ProductSkuEntity sku : rows) {
            if (!ProductSkuEntity.PUBLISHED.equals(sku.getStatus()) || sku.available() <= 0) {
                continue;
            }
            if (sku.isDefault()) {
                return sku;
            }
            if (first == null) {
                first = sku;
            }
        }
        return first;
    }

    private AiRankingResult rank(StylistRequestDto request, List<AiCandidate> pool, boolean setMode) {
        if (properties.providerReady()) {
            AiRankingResult fromModel = tryGemini(request, pool, setMode);
            if (fromModel != null) {
                return fromModel;
            }
        }
        return rulesResult(request, pool, setMode);
    }

    private AiRankingResult tryGemini(StylistRequestDto request, List<AiCandidate> pool, boolean setMode) {
        try {
            List<AiRankedPick> valid = sanitize(gemini.rank(request, pool, setMode), pool);
            int needed = Math.min(minimum(setMode), pool.size());
            if (!valid.isEmpty() && valid.size() >= needed) {
                return new AiRankingResult(AiRankingResult.SOURCE_AI, null, null, valid);
            }
            return null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private AiRankingResult rulesResult(StylistRequestDto request, List<AiCandidate> pool, boolean setMode) {
        AiRankingResult ranked = rules.rank(request, pool, setMode);
        List<AiRankedPick> picks = ranked == null || ranked.picks() == null ? List.of() : ranked.picks();
        return new AiRankingResult(AiRankingResult.SOURCE_FALLBACK, "rule_based",
                AiRankingResult.FALLBACK_BANNER, picks);
    }

    private AiAssembledResult finishSet(StylistRequestDto request, AiRankingResult ranked, List<AiCandidate> pool) {
        return assembled(ranked, selectSet(request, decorate(pool, ranked, pool.size())));
    }

    private List<AiDecoratedPick> selectSet(StylistRequestDto request, List<AiDecoratedPick> ordered) {
        boolean setBudget = AiBudgetBand.SET.equals(AiBudgetBand.scope(request)) && request.getBudget() != null;
        BigDecimal upper = AiBudgetBand.of(request.getBudget(), properties.getBudgetToleranceRatio()).upper();
        List<AiDecoratedPick> chosen = new ArrayList<>();
        Set<Long> slots = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        boolean budgetBlocked = false;
        for (AiDecoratedPick pick : ordered) {
            if (chosen.size() >= Math.max(1, properties.getMaxSetItems())) {
                break;
            }
            if (!slots.add(slot(pick))) {
                continue;
            }
            BigDecimal next = total.add(priceOf(pick));
            if (setBudget && next.compareTo(upper) > 0) {
                slots.remove(slot(pick));
                budgetBlocked = true;
                continue;
            }
            chosen.add(pick);
            total = next;
        }
        ensureSetSize(chosen, budgetBlocked);
        return chosen;
    }

    private void ensureSetSize(List<AiDecoratedPick> chosen, boolean budgetBlocked) {
        if (chosen.size() >= Math.max(1, properties.getMinSetItems())) {
            return;
        }
        if (budgetBlocked) {
            throw budgetExceeded();
        }
        throw invalidSet("Không đủ món khác danh mục để tạo bộ");
    }

    private List<AiCandidate> excludeDisliked(List<AiCandidate> source, Long userId) {
        if (userId == null) {
            return source;
        }
        List<Long> ids = feedback.findDislikedProductIds(userId);
        if (ids == null || ids.isEmpty()) {
            return source;
        }
        Set<Long> disliked = new HashSet<>(ids);
        List<AiCandidate> out = new ArrayList<>();
        for (AiCandidate candidate : source) {
            if (!disliked.contains(candidate.productId())) {
                out.add(candidate);
            }
        }
        return out;
    }

    private static List<AiCandidate> preferOccasion(List<AiCandidate> source, String occasion) {
        if (occasion == null || occasion.isBlank()) {
            return source;
        }
        List<AiCandidate> matched = new ArrayList<>();
        for (AiCandidate candidate : source) {
            if (occasion.equalsIgnoreCase(candidate.occasion())) {
                matched.add(candidate);
            }
        }
        return matched.isEmpty() ? source : matched;
    }

    private static List<AiCandidate> filterPrice(List<AiCandidate> source, AiBudgetBand band, boolean loose) {
        List<AiCandidate> out = new ArrayList<>();
        for (AiCandidate candidate : source) {
            if (band.fits(candidate.price(), loose)) {
                out.add(candidate);
            }
        }
        return out;
    }

    private static List<AiCandidate> cap(List<AiCandidate> source, int max) {
        if (source.size() <= max) {
            return source;
        }
        List<AiCandidate> copy = new ArrayList<>(source);
        copy.sort(Comparator.comparingLong(AiCandidate::productId));
        return new ArrayList<>(copy.subList(0, max));
    }

    private static List<AiDecoratedPick> decorate(List<AiCandidate> pool, AiRankingResult ranked, int limit) {
        Map<Long, AiCandidate> byId = new LinkedHashMap<>();
        for (AiCandidate candidate : pool) {
            byId.put(candidate.productId(), candidate);
        }
        List<AiDecoratedPick> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        List<AiRankedPick> picks = ranked.picks() == null ? List.of() : ranked.picks();
        for (AiRankedPick pick : picks) {
            if (out.size() >= limit || pick == null || !seen.add(pick.productId())) {
                continue;
            }
            AiCandidate candidate = byId.get(pick.productId());
            if (candidate != null) {
                out.add(new AiDecoratedPick(candidate, pick.reason(), pick.matchScore()));
            }
        }
        return out;
    }

    private static List<AiRankedPick> sanitize(AiRankingResult result, List<AiCandidate> pool) {
        if (result == null || result.picks() == null) {
            return List.of();
        }
        Set<Long> allowed = new HashSet<>();
        for (AiCandidate candidate : pool) {
            allowed.add(candidate.productId());
        }
        List<AiRankedPick> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (AiRankedPick pick : result.picks()) {
            if (pick != null && allowed.contains(pick.productId()) && seen.add(pick.productId())) {
                out.add(pick);
            }
        }
        return out;
    }

    private static AiAssembledResult assembled(AiRankingResult ranked, List<AiDecoratedPick> picks) {
        return new AiAssembledResult(ranked.source(), ranked.banner(), ranked.fallbackReason(), picks, sum(picks));
    }

    private static BigDecimal sum(List<AiDecoratedPick> picks) {
        BigDecimal total = BigDecimal.ZERO;
        for (AiDecoratedPick pick : picks) {
            total = total.add(priceOf(pick));
        }
        return total;
    }

    private static BigDecimal priceOf(AiDecoratedPick pick) {
        return pick.candidate().price() == null ? BigDecimal.ZERO : pick.candidate().price();
    }

    private static Long slot(AiDecoratedPick pick) {
        Long categoryId = pick.candidate().categoryId();
        return categoryId != null ? categoryId : -pick.candidate().productId();
    }

    private int minimum(boolean setMode) {
        int configured = setMode ? properties.getMinSetItems() : properties.getMinRecommendations();
        return Math.max(1, configured);
    }

    private static boolean retryWithRules(AiRankingResult ranked, BusinessException ex) {
        if (ranked == null || !AiRankingResult.SOURCE_AI.equals(ranked.source())) {
            return false;
        }
        return ErrorCode.AI_SET_INVALID.equals(ex.getCode()) || ErrorCode.AI_BUDGET_EXCEEDED.equals(ex.getCode());
    }

    private static BusinessException budgetExceeded() {
        return BusinessException.badRequest(ErrorCode.AI_BUDGET_EXCEEDED,
                "Không có sản phẩm trong khoảng ngân sách cho phép");
    }

    private static BusinessException invalidSet(String message) {
        return BusinessException.badRequest(ErrorCode.AI_SET_INVALID, message);
    }
}
