package com.pk.core.model.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Dữ liệu PIM mở rộng dùng chung cho tạo/sửa sản phẩm (V18). Khi tạo: field null = không có. Khi sửa (PUT = thay thế):
 * các field vô hướng null = xoá giá trị đã lưu (trừ unitOfMeasure null = giữ nguyên); {@code productInfos} và
 * {@code productMixMatch} null = giữ nguyên, [] = xoá hết, có phần tử = thay toàn bộ.
 */
@Getter
@Setter
@NoArgsConstructor
public class AdminProductExtraRequestDto {

    /** EACH | STRAND | PAIR | SET | BOX | BAG | SHEET (bỏ trống khi tạo = EACH). */
    private String unitOfMeasure;
    /** Thuế suất riêng (%) 0..100, null = theo mặc định. */
    @DecimalMin("0")
    @DecimalMax("100")
    private BigDecimal taxRateOverride;
    @PositiveOrZero
    private BigDecimal weightGram;
    @PositiveOrZero
    private BigDecimal length;
    @PositiveOrZero
    private BigDecimal width;
    @PositiveOrZero
    private BigDecimal height;
    @Size(max = 200)
    private String metaTitle;
    @Size(max = 500)
    private String metaDescription;
    @Size(max = 500)
    private String metaKeywords;

    /** Thương hiệu / mùa (V27), tuỳ chọn; PUT không gửi = bỏ gán. */
    private Long brandId;
    private Long seasonId;
    @Size(max = 50, message = "Tối đa 50 khối thông tin bổ sung")
    private List<@Valid @NotNull Info> productInfos;
    /** Id các sản phẩm phối kèm (tối đa 20, không trùng, không gồm chính nó, phải tồn tại). */
    @Size(max = 20, message = "Tối đa 20 sản phẩm phối kèm")
    private List<@NotNull Long> productMixMatch;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Info {
        @Size(max = 200)
        private String title;
        @NotBlank(message = "Nội dung thông tin bổ sung không được để trống")
        @Size(max = 4000)
        private String description;
        @PositiveOrZero
        private Integer sortOrder;
    }
}
