package com.pk.core.model.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Điều kiện export sản phẩm ra CSV (mỗi dòng = 1 SKU). Ưu tiên: `productIds` (xuất các sản phẩm đã chọn); nếu không có thì
 * dùng bộ lọc giống danh sách admin; `isPagination=true` chỉ xuất đúng trang `page`/`take`, ngược lại xuất toàn bộ kết quả lọc.
 */
@Getter
@Setter
public class IeProductExportRequestDto {

    private List<Long> productIds;
    private String search;
    private String status;
    private String categorySlug;
    private String collectionSlug;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private String timeFilter;
    private String timeFrom;
    private String timeTo;
    private Boolean hasPendingApproval;
    private String approvalStatus;
    private Boolean isPagination;
    private Integer page;
    private Integer take;
    /** Tuỳ chọn: gắn job với 1 đối tượng bên ngoài (vd. referenceType=CAMPAIGN). */
    private String referenceType;
    private String referenceId;
}
