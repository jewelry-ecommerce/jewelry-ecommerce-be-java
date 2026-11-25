package com.pk.core.business.ai.tryon;

import com.pk.core.business.ai.AiCandidate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Chọn 2–3 SP published khác phong cách theo vùng cơ thể đã detect. */
public final class TryOnCatalogPicker {

    private TryOnCatalogPicker() {
    }

    public static List<Picked> pick(List<AiCandidate> catalog, List<String> regions, List<String> styles, int max) {
        List<AiCandidate> matched = filterByRegions(catalog, regions);
        if (matched.isEmpty()) {
            return List.of();
        }
        List<String> useStyles = styles == null || styles.isEmpty() ? TryOnBodyRegion.STYLES : styles;
        int limit = Math.min(Math.max(2, max), 3);
        List<Picked> picks = new ArrayList<>();
        Set<Long> usedIds = new HashSet<>();
        for (String style : useStyles) {
            if (picks.size() >= limit) {
                break;
            }
            AiCandidate chosen = firstUnused(matched, usedIds, picks.size());
            if (chosen == null) {
                break;
            }
            usedIds.add(chosen.productId());
            picks.add(new Picked(style, regionFor(chosen, regions), chosen));
        }
        while (picks.size() < Math.min(2, matched.size()) && picks.size() < limit) {
            AiCandidate chosen = firstUnused(matched, usedIds, picks.size());
            if (chosen == null) {
                break;
            }
            usedIds.add(chosen.productId());
            String style = useStyles.get(picks.size() % useStyles.size());
            picks.add(new Picked(style, regionFor(chosen, regions), chosen));
        }
        return picks;
    }

    private static List<AiCandidate> filterByRegions(List<AiCandidate> catalog, List<String> regions) {
        Set<String> keywords = new HashSet<>();
        for (String region : regions) {
            keywords.addAll(TryOnBodyRegion.categoryKeywords(region));
        }
        if (keywords.isEmpty()) {
            return List.of();
        }
        List<AiCandidate> matched = new ArrayList<>();
        for (AiCandidate candidate : catalog) {
            if (matches(candidate, keywords)) {
                matched.add(candidate);
            }
        }
        return matched;
    }

    private static boolean matches(AiCandidate candidate, Set<String> keywords) {
        String haystack = ((candidate.categoryName() == null ? "" : candidate.categoryName()) + " "
                + (candidate.name() == null ? "" : candidate.name())).toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (haystack.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static AiCandidate firstUnused(List<AiCandidate> matched, Set<Long> usedIds, int offset) {
        int start = Math.floorMod(offset * 3, matched.size());
        for (int i = 0; i < matched.size(); i++) {
            AiCandidate candidate = matched.get((start + i) % matched.size());
            if (!usedIds.contains(candidate.productId())) {
                return candidate;
            }
        }
        return null;
    }

    private static String regionFor(AiCandidate candidate, List<String> regions) {
        for (String region : regions) {
            if (matches(candidate, TryOnBodyRegion.categoryKeywords(region))) {
                return region;
            }
        }
        return regions.isEmpty() ? TryOnBodyRegion.FINGER : regions.get(0);
    }

    public record Picked(String style, String region, AiCandidate product) {
    }
}
