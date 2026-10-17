package com.pk.core.business.service;

import java.util.Map;

/**
 * Chạy thử và tra cứu khuyến mãi/voucher cho Admin FE (sandbox mô phỏng giỏ hàng, danh sách voucher khi tạo đơn, tra khách theo SĐT).
 * Kết quả tính theo cùng công thức với engine giá thật (PromotionEntity.computeUnitDiscount / VoucherEntity.computeDiscount):
 * mỗi dòng hàng chỉ nhận khuyến mãi sản phẩm có mức giảm lớn nhất, voucher tính trên giá sau khuyến mãi.
 */
public interface PromotionRuntimeService {

    /** Đánh giá giỏ hàng với các chiến dịch đã phát hành đang chạy. */
    Map<String, Object> evaluate(Map<String, Object> request);

    /** Như evaluate nhưng cộng thêm các hình thức của bản nháp, kèm so sánh trước/sau và xung đột. */
    Map<String, Object> simulate(String draftId, Map<String, Object> request);

    /** Voucher của bản nháp + voucher đang chạy để chọn khi mô phỏng. */
    Map<String, Object> simulationVouchers(String draftId, Map<String, Object> request);

    /** Danh sách voucher dùng được/không dùng được cho giỏ hàng (checkoutContext hoặc items của tạo đơn). */
    Map<String, Object> discoverVouchers(Map<String, Object> request);

    /** Kiểm tra một mã voucher: hợp lệ hay không kèm thẻ voucher. */
    Map<String, Object> validateCode(Map<String, Object> request);

    /** Tra khách hàng theo số điện thoại. */
    Map<String, Object> customerByPhone(String phone);
}
