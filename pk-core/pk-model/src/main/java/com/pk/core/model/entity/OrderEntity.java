package com.pk.core.model.entity;

import com.pk.core.model.constant.TableConstant;
import com.pk.core.common.entity.UpdateEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import vn.com.unit.miragesql.miragesql.annotation.Column;
import vn.com.unit.miragesql.miragesql.annotation.PrimaryKey;
import vn.com.unit.miragesql.miragesql.annotation.PrimaryKey.GenerationType;
import vn.com.unit.miragesql.miragesql.annotation.Table;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Đơn hàng - bảng `orders` KHÔNG có deleted_id/deleted_date (không xoá mềm đơn hàng) nên extends
 * UpdateEntity, không phải BaseEntity (khác Category/Product/Voucher...). Địa chỉ giao hàng SAO CHÉP
 * (snapshot) từ CustomerAddressEntity vào các cột ship_* lúc tạo đơn - sửa/xoá địa chỉ gốc sau đó
 * không ảnh hưởng đơn đã đặt. */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.ORDERS)
public class OrderEntity extends UpdateEntity {

    public static final String PENDING = "PENDING";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String SHIPPING = "SHIPPING";
    public static final String DELIVERED = "DELIVERED";
    public static final String CANCELLED = "CANCELLED";
    public static final String RETURNED = "RETURNED";

    public static final String UNPAID = "UNPAID";
    public static final String PAID = "PAID";
    public static final String REFUNDED = "REFUNDED";

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "orders_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "code")
    private String code;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "guest_email")
    private String guestEmail;

    @Column(name = "status")
    private String status = PENDING;

    @Column(name = "payment_status")
    private String paymentStatus = UNPAID;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "shipping_method")
    private String shippingMethod;

    @Column(name = "tracking_code")
    private String trackingCode;

    @Column(name = "ship_recipient_name")
    private String shipRecipientName;

    @Column(name = "ship_phone")
    private String shipPhone;

    @Column(name = "ship_province")
    private String shipProvince;

    @Column(name = "ship_district")
    private String shipDistrict;

    @Column(name = "ship_ward")
    private String shipWard;

    @Column(name = "ship_address_line")
    private String shipAddressLine;

    @Column(name = "subtotal")
    private BigDecimal subtotal;

    @Column(name = "product_discount")
    private BigDecimal productDiscount = BigDecimal.ZERO;

    @Column(name = "voucher_discount")
    private BigDecimal voucherDiscount = BigDecimal.ZERO;

    @Column(name = "shipping_fee")
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Column(name = "grand_total")
    private BigDecimal grandTotal;

    @Column(name = "applied_voucher_code")
    private String appliedVoucherCode;

    /** Đã chốt xuất tồn (on_hand -= qty, reserved -= qty) khi giao - đúng một lần. */
    @Column(name = "stock_fulfilled")
    private boolean stockFulfilled;

    /** Tổng tiền đã hoàn (ghi nhận hoàn tiền cơ bản, chưa chuyển tiền qua cổng). */
    @Column(name = "refunded_amount")
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    /** Nguồn tạo đơn: WEBSITE (khách tự đặt) hoặc ADMIN (nhân viên tạo hộ). */
    @Column(name = "source")
    private String source = "WEBSITE";

    @Column(name = "note")
    private String note;

    @Column(name = "placed_at")
    private Date placedAt = new Date();

    public OrderEntity(Long userId, String paymentMethod, String shippingMethod, CustomerAddressEntity shipTo,
                        BigDecimal subtotal, BigDecimal productDiscount, BigDecimal voucherDiscount,
                        BigDecimal shippingFee, String appliedVoucherCode, String note) {
        this.code = generateCode();
        this.userId = userId;
        this.paymentMethod = paymentMethod;
        this.shippingMethod = shippingMethod;
        this.shipRecipientName = shipTo.getRecipientName();
        this.shipPhone = shipTo.getPhone();
        this.shipProvince = shipTo.getProvince();
        this.shipDistrict = shipTo.getDistrict();
        this.shipWard = shipTo.getWard();
        this.shipAddressLine = shipTo.getAddressLine();
        this.subtotal = subtotal;
        this.productDiscount = productDiscount;
        this.voucherDiscount = voucherDiscount;
        this.shippingFee = shippingFee;
        this.grandTotal = subtotal.subtract(productDiscount).subtract(voucherDiscount).add(shippingFee);
        this.appliedVoucherCode = appliedVoucherCode;
        this.note = note;
    }

    /** Trạng thái kế tiếp hợp lệ cho PATCH /admin/orders/{code}/status (mục L spec) - CHỈ áp dụng
     * cho luồng tiến PENDING->CONFIRMED->SHIPPING->DELIVERED và huỷ (->CANCELLED). RETURNED KHÔNG
     * nằm trong map này vì đi qua POST .../return riêng (có lineIds[], validate bằng isReturnable()
     * thay vì map này - xem AdminOrderRest/OrderServiceImpl.returnOrder()). DELIVERED không map tới
     * đâu nữa qua PATCH status (map rỗng) - muốn RETURNED phải gọi endpoint /return riêng. */
    private static final Map<String, Set<String>> NEXT_STATUSES = Map.of(
            PENDING, Set.of(CONFIRMED, CANCELLED),
            CONFIRMED, Set.of(SHIPPING, CANCELLED),
            SHIPPING, Set.of(DELIVERED, CANCELLED),
            DELIVERED, Set.of()
    );

    /** Có được PATCH status hiện tại -> newStatus hay không (dùng cho admin, xem NEXT_STATUSES). */
    public boolean canTransitionTo(String newStatus) {
        return NEXT_STATUSES.getOrDefault(status, Set.of()).contains(newStatus);
    }

    /** Admin huỷ đơn (POST /admin/orders/{code}/cancel) - RỘNG hơn isCancellableByCustomer() (khách
     * chỉ huỷ được PENDING/CONFIRMED): admin còn huỷ được cả khi đang SHIPPING. */
    public boolean isCancellableByAdmin() {
        return PENDING.equals(status) || CONFIRMED.equals(status) || SHIPPING.equals(status);
    }

    /** Chỉ xác nhận hoàn trả (POST /admin/orders/{code}/return) được khi đơn đã DELIVERED. */
    public boolean isReturnable() {
        return DELIVERED.equals(status);
    }

    public void markReturned() {
        this.status = RETURNED;
    }

    /** Khách chỉ được tự huỷ khi đơn còn PENDING/CONFIRMED (chưa giao) - theo mô tả spec FE "Khách
     * huỷ đơn trước khi giao". Từ SHIPPING trở đi phải qua admin (xem isCancellableByAdmin() - Admin
     * mục L, 2026-09-29). */
    public boolean isCancellableByCustomer() {
        return PENDING.equals(status) || CONFIRMED.equals(status);
    }

    public void cancel() {
        this.status = CANCELLED;
    }

    /** Mốc thời gian mili-giây + số ngẫu nhiên 3 chữ số - đủ dùng cho quy mô đồ án, KHÔNG đảm bảo
     * tuyệt đối duy nhất dưới tải rất cao đồng thời (cột code vẫn có UNIQUE ở DB để chặn nếu trùng). */
    private static String generateCode() {
        return "DH" + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(100, 999);
    }
}
