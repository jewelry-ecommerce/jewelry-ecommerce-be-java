package com.pk.core.business.ai.tryon;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Vùng cơ thể hỗ trợ try-on và từ khóa danh mục catalog (tập trung, không hardcode rải rác). */
public final class TryOnBodyRegion {

    public static final String WRIST = "wrist";
    public static final String ANKLE = "ankle";
    public static final String NECK = "neck";
    public static final String EAR = "ear";
    public static final String FINGER = "finger";
    public static final String HAND_COMBO = "hand_combo";
    public static final String UNSUPPORTED = "unsupported";

    public static final List<String> SUPPORTED = List.of(WRIST, ANKLE, NECK, EAR, FINGER, HAND_COMBO);

    public static final List<String> STYLES = List.of("minimal", "statement", "everyday");

    private TryOnBodyRegion() {
    }

    public static boolean supported(String region) {
        return region != null && SUPPORTED.contains(region.trim().toLowerCase(Locale.ROOT));
    }

    /** Từ khóa khớp tên danh mục / tên SP (tiếng Việt catalog). */
    public static Set<String> categoryKeywords(String region) {
        String key = region == null ? "" : region.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case WRIST -> Set.of("lắc", "vòng", "bracelet", "charm");
            case ANKLE -> Set.of("lắc", "chân", "ankle", "charm");
            case NECK -> Set.of("dây", "chuyền", "necklace", "mặt");
            case EAR -> Set.of("bông", "tai", "khuyên", "earring");
            case FINGER, HAND_COMBO -> Set.of("nhẫn", "ring", "charm");
            default -> Set.of();
        };
    }

    public static String styleLabel(String style) {
        String key = style == null ? "" : style.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "minimal" -> "Tối giản";
            case "statement" -> "Nổi bật";
            case "everyday" -> "Hàng ngày";
            default -> "Gợi ý";
        };
    }
}
