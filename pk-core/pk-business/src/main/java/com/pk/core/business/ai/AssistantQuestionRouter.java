package com.pk.core.business.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Chào và câu về shop không tìm hàng. Câu có tên món hoặc link mới query catalog. */
public final class AssistantQuestionRouter {

    private static final Pattern SLUG = Pattern.compile("(?i)san-pham/([a-z0-9-]+)");
    private static final Pattern WORD = Pattern.compile("[^\\p{L}\\p{Nd}]+");
    private static final Locale VI = Locale.forLanguageTag("vi");
    private static final int MAX_PHRASE = 80;

    private static final Set<String> SKIP = Set.of(
            "tôi", "muốn", "mua", "tư", "vấn", "cho", "cái", "một", "với", "của", "là", "có", "không",
            "nào", "gì", "được", "bạn", "em", "này", "đó", "những", "các", "và", "hoặc", "để", "khi",
            "thì", "rất", "quá", "giúp", "xem", "hỏi", "về", "cần", "xin", "làm", "sao", "thế", "ạ", "nhé",
            "nha", "nhe", "shop", "mình", "ơi", "oi", "ah", "bao", "nhiêu", "nhieu", "lâu", "lau",
            "tiền", "tien", "mấy", "may");

    private static final Set<String> GREETING = Set.of(
            "chào", "hello", "hi", "hey", "alo", "chao");

    private static final Set<String> POLICY = Set.of(
            "phí", "phi", "ship", "giao", "thanh", "toán", "toan", "payos", "cod", "đổi", "doi",
            "trả", "tra", "bảo", "hành", "hanh", "liên", "hệ", "he", "đơn", "don", "đặt", "dat");

    private static final Set<String> GENERIC = Set.of(
            "trang", "sức", "suc", "sản", "phẩm", "pham", "món", "mon", "đồ", "do", "hàng", "hang", "sp");

    private AssistantQuestionRouter() {
    }

    public static AssistantRoute route(String message) {
        String raw = message == null ? "" : message.trim();
        String slug = readSlug(raw);
        List<String> words = words(withoutLinks(raw));
        List<String> productWords = productWords(words);
        if (slug != null || !productWords.isEmpty()) {
            return new AssistantRoute(AssistantRoute.PRODUCT, phrase(productWords), slug);
        }
        if (words.isEmpty() || greeting(words)) {
            return new AssistantRoute(AssistantRoute.GREETING, "", null);
        }
        return new AssistantRoute(AssistantRoute.SHOP, "", null);
    }

    private static String withoutLinks(String raw) {
        return raw.replaceAll("(?i)https?://\\S+", " ").replaceAll("(?i)san-pham/[a-z0-9-]+", " ");
    }

    private static String readSlug(String raw) {
        Matcher matcher = SLUG.matcher(raw);
        return matcher.find() ? matcher.group(1).toLowerCase(Locale.ROOT) : null;
    }

    private static List<String> words(String raw) {
        String normalized = WORD.matcher(raw.toLowerCase(VI)).replaceAll(" ").trim();
        if (normalized.isEmpty()) {
            return List.of();
        }
        return List.of(normalized.split(" +"));
    }

    private static List<String> productWords(List<String> words) {
        List<String> kept = new ArrayList<>();
        for (String word : words) {
            if (!skip(word)) {
                kept.add(word);
            }
        }
        return kept;
    }

    private static boolean skip(String word) {
        return SKIP.contains(word) || GREETING.contains(word) || POLICY.contains(word) || GENERIC.contains(word);
    }

    private static boolean greeting(List<String> words) {
        for (String word : words) {
            if (!GREETING.contains(word) && !SKIP.contains(word)) {
                return false;
            }
        }
        return true;
    }

    private static String phrase(List<String> words) {
        String joined = String.join(" ", words).trim();
        return joined.length() <= MAX_PHRASE ? joined : joined.substring(0, MAX_PHRASE).trim();
    }
}
