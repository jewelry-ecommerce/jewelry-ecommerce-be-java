package com.pk.core.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Kết quả try-on. HTTP luôn 200 khi xử lý xong nghiệp vụ;
 * {@code ok=false} + message tiếng Việt khi ảnh không hợp / AI tắt / không có SP.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TryOnResponseDto {
    private boolean ok;
    private String message;
    private String disclaimer;
    private List<String> detectedRegions;
    private List<TryOnVariantResponseDto> variants;

    public static final String DISCLAIMER =
            "Ảnh minh họa AI; sản phẩm mua là đúng hàng trên web (lookalike, không cam kết pixel-đúng 100%).";

    public static TryOnResponseDto fail(String message) {
        return fail(message, List.of());
    }

    public static TryOnResponseDto fail(String message, List<String> detectedRegions) {
        return TryOnResponseDto.builder()
                .ok(false)
                .message(message)
                .disclaimer(DISCLAIMER)
                .detectedRegions(detectedRegions == null ? List.of() : detectedRegions)
                .variants(List.of())
                .build();
    }

    public static TryOnResponseDto success(List<String> regions, List<TryOnVariantResponseDto> variants) {
        return TryOnResponseDto.builder()
                .ok(true)
                .message(null)
                .disclaimer(DISCLAIMER)
                .detectedRegions(regions)
                .variants(variants)
                .build();
    }
}
