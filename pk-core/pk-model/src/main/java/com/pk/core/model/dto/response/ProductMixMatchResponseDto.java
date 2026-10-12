package com.pk.core.model.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.pk.core.model.entity.ProductEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Sản phẩm phối kèm: đủ để hiển thị (tên, mã, ảnh, giá từ) mà không phải gọi thêm. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductMixMatchResponseDto {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;
    private String code;
    private String name;
    private String slug;
    private String status;
    private String thumbnailUrl;
    /** Giá thấp nhất các SKU chưa lưu trữ (products.base_price). */
    private BigDecimal basePrice;

    /** Cùng giá trị code, theo tên field FE (`spuCode`). */
    public String getSpuCode() {
        return code;
    }

    /** Cùng giá trị thumbnailUrl, theo tên field FE (`image`). */
    public String getImage() {
        return thumbnailUrl;
    }

    public static ProductMixMatchResponseDto from(ProductEntity p) {
        return new ProductMixMatchResponseDto(p.getId(), p.getCode(), p.getName(), p.getSlug(), p.getStatus(),
                p.getThumbnailUrl(), p.getBasePrice());
    }
}
