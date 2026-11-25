package com.pk.core.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Một mẫu lookalike try-on kèm thẻ sản phẩm catalog thật. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TryOnVariantResponseDto {
    private String style;
    private String styleLabel;
    private String tryOnImageUrl;
    private Long productId;
    private String productName;
    private String productSlug;
    private String productThumbnailUrl;
    private BigDecimal price;
    private String categoryName;
}
