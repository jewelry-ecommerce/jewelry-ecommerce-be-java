package com.pk.core.business.service;

import java.math.BigDecimal;

/** Tính khuyến mãi sản phẩm (markdown/đồng giá) áp vào giá bán - dùng chung cho giỏ hàng, báo giá
 * checkout, tạo đơn và hiển thị giá storefront để mọi nơi ra cùng một con số. Quy ước: lấy khuyến mãi
 * đang hiệu lực có mức giảm LỚN NHẤT trên mỗi đơn vị, KHÔNG cộng dồn nhiều chương trình; giá nền
 * là giá thực bán của SKU (đã gồm sale_price nếu có). */
public interface PromotionPricingService {

    /** Số tiền giảm trên 1 đơn vị sản phẩm `productId` bán giá `unitPrice`; 0 nếu không có khuyến mãi. */
    BigDecimal unitDiscount(Long productId, BigDecimal unitPrice);

    /**
     * Như trên nhưng theo SKU: khuyến mãi có phạm vi SKU (sinh từ chiến dịch wizard, bảng promotion_scope_skus) chỉ áp cho
     * SKU thuộc phạm vi - SKU anh em cùng sản phẩm KHÔNG được giảm. MỌI nơi tính tiền thật phải dùng overload này.
     */
    BigDecimal unitDiscount(Long productId, Long skuId, BigDecimal unitPrice);
}
