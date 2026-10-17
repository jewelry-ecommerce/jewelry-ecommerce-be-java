package com.pk.core.business.service;

import com.pk.core.model.dto.request.AdminVoucherRequestDto;
import com.pk.core.model.dto.response.VoucherResponseDto;
import com.pk.core.model.entity.VoucherEntity;

import java.math.BigDecimal;
import java.util.List;

/** Áp voucher lúc đặt hàng (OrderServiceImpl gọi) - chưa có API storefront riêng để tra cứu voucher
 * (spec FE không liệt kê endpoint này, voucher chỉ xuất hiện trong request/response của checkout/order).
 * Từ 2026-09-29 thêm CRUD cho Admin (mục K). */
public interface VoucherService {

    /** Kiểm tra voucher hợp lệ để áp cho đơn có `subtotal` - ném ResourceNotFoundException (mã không
     * tồn tại) hoặc BusinessException (VOUCHER_NOT_STARTED/VOUCHER_EXPIRED/VOUCHER_USAGE_LIMIT_REACHED/
     * VOUCHER_MIN_ORDER_NOT_MET) nếu không hợp lệ. KHÔNG tăng used_count ở đây (xem applyUsage). */
    VoucherEntity validate(String code, BigDecimal subtotal);

    /** Dòng hàng đưa vào tính voucher: `net` = thành tiền dòng SAU khuyến mãi sản phẩm. */
    record Line(Long skuId, BigDecimal net) {
    }

    /** Kết quả áp voucher: `discount` tính trên phần tiền của các dòng đủ điều kiện. */
    record Applied(VoucherEntity voucher, BigDecimal discount) {
    }

    /**
     * Áp voucher cho các dòng hàng - MỘT quy tắc dùng chung cho checkout, báo giá, tạo/sửa đơn (cùng công thức engine
     * wizard): voucher có phạm vi SKU (sinh từ chiến dịch, bảng voucher_scope_skus) chỉ giảm trên các dòng thuộc phạm vi;
     * giá trị tối thiểu so với tổng các dòng đủ điều kiện; không có dòng nào đủ điều kiện -> 400 VOUCHER_NO_ELIGIBLE_ITEMS.
     * Voucher không có phạm vi (voucher cũ) hành xử như validate(code, tổng các dòng).
     */
    Applied apply(String code, List<Line> lines);

    /** Tăng used_count nguyên tử - gọi SAU khi đơn đã tạo thành công, trong cùng transaction với
     * OrderServiceImpl.create(). Ném BusinessException(VOUCHER_USAGE_LIMIT_REACHED) nếu vừa hết lượt
     * (đua với request khác) dù validate() lúc trước còn lượt. `code` chỉ để đưa vào thông báo lỗi
     * (khỏi phải truy vấn lại voucher chỉ để lấy code, nơi gọi đã có sẵn từ validate()). */
    void applyUsage(Long voucherId, String code);

    // ===================== ADMIN (mục K spec) =====================

    List<VoucherResponseDto> findAllForAdmin();

    /** Tạo voucher - POST /admin/vouchers. 409 nếu trùng `code` (dùng lại DUPLICATE_CODE, spec không
     * đặt tên riêng cho voucher trùng mã, chỉ nói chung "DUPLICATE_CODE/DUPLICATE_SLUG/DUPLICATE_SKU"
     * ở mục Catalog - áp dụng tinh thần tương tự cho voucher). */
    VoucherResponseDto create(AdminVoucherRequestDto req);

    VoucherResponseDto update(Long voucherId, AdminVoucherRequestDto req);

    /** "Sửa/khoá-xoá" (spec) - bảng vouchers KHÔNG có cột deleted_id/deleted_date (không như
     * product/category/collection) nên xoá THẬT (hard delete) thay vì xoá mềm - CHƯA hỏi lại xác
     * nhận, giả định hợp lý nhất theo đúng schema hiện có; orders.applied_voucher_code chỉ là VARCHAR
     * lưu lại mã dùng lúc đặt hàng (không phải FK) nên xoá voucher không phá dữ liệu đơn hàng cũ. */
    void delete(Long voucherId);

    /** Trả lại 1 lượt dùng của voucher theo mã (khi đơn áp voucher bị huỷ). Mã không còn tồn tại thì bỏ qua. */
    void releaseUsage(String code);
}
