package com.pk.core.business.repository;

import com.pk.core.model.entity.OrderEntity;
import org.springframework.data.repository.query.Param;
import vn.com.unit.springframework.data.mirage.repository.query.Modifying;

import java.util.List;

public interface OrderRepo extends PkRepo<OrderEntity, Long> {

    /** Trả null nếu không có hoặc không thuộc user này - tránh lộ đơn của người khác (IDOR). */
    OrderEntity findByCodeAndUserId(@Param("code") String code, @Param("userId") Long userId);

    /** KHÔNG lọc theo user - chỉ dùng ở PaymentServiceImpl (webhook cổng thanh toán gọi vào, không
     * có người dùng đăng nhập nên không có userId để lọc theo IDOR như findByCodeAndUserId). */
    OrderEntity findByCode(@Param("code") String code);

    List<OrderEntity> findByUserId(@Param("userId") Long userId);

    /** Tìm đơn cho Admin mục L (GET /admin/orders) - lọc rỗng = trả tất cả (cùng quy ước
     * ProductRepo.searchAdmin). fromDate/toDate KHÔNG lọc ở đây (đọc toàn bộ rồi lọc placedAt bằng
     * Java ở OrderServiceImpl - tránh đoán sai cú pháp bind kiểu Date của Mirage, xem
     * OrderServiceImpl.searchForAdmin()). */
    List<OrderEntity> searchAdmin(@Param("status") String status, @Param("paymentStatus") String paymentStatus,
                                   @Param("keyword") String keyword);

    /** Đổi trạng thái NGUYÊN TỬ chỉ khi đơn vẫn đang ở `fromStatus`. Trả 0 nếu request khác đã đổi trước
     * (vd 2 lần huỷ đồng thời) - nơi gọi phải dừng, tránh nhả tồn/voucher hai lần. */
    @Modifying
    int updateStatusIfCurrent(@Param("id") Long id, @Param("fromStatus") String fromStatus,
                              @Param("toStatus") String toStatus);

    /** Ghi nhận hoàn tiền NGUYÊN TỬ: chỉ khi đơn đang PAID và tổng đã hoàn + amount <= tổng đơn. Hoàn đủ thì payment_status = REFUNDED,
     * hoàn một phần thì giữ PAID. Trả 0 nếu vượt số còn hoàn được hoặc đơn chưa thanh toán. */
    @Modifying
    int addRefund(@Param("id") Long id, @Param("amount") java.math.BigDecimal amount);
}
