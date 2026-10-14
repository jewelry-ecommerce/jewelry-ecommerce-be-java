package com.pk.core.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** Một nhóm bộ lọc storefront (khớp IProductFilterSection của client): id/label = thuộc tính, options = giá trị. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductFilterSectionDto {

    private String id;
    private String label;
    private String code;
    private String displayType;
    private List<Option> options;

    /** value = id giá trị thuộc tính (dùng cho query `attributes=`), code = mã giá trị, count = số sản phẩm khớp. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Option {
        private String value;
        private String label;
        private int count;
        private String code;
        private String imageUrl;
    }
}
