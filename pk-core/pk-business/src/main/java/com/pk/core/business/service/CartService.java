package com.pk.core.business.service;

import com.pk.core.model.dto.request.CartItemQuantityRequestDto;
import com.pk.core.model.dto.request.CartSyncRequestDto;
import com.pk.core.model.dto.request.CartTotalRequestDto;
import com.pk.core.model.dto.response.CartRecommendationResponseDto;
import com.pk.core.model.dto.response.CartSyncResponseDto;
import com.pk.core.model.dto.response.CartTotalResponseDto;

/**
 * Giỏ hàng theo spec FE (/storefront/cart/cart). Giỏ thuộc về khách đăng nhập (userId) HOẶC khách vãng lai
 * (guestId do server cấp, FE gửi lại ở header X-Guest-Cart-Id). Khi cả hai cùng có, ưu tiên userId.
 * Giá là số nguyên VND. Thêm 2026-10-06.
 */
public interface CartService {

    /** Xem giỏ hiện tại. Chưa có giỏ -> danh sách rỗng (không tạo giỏ mới). */
    CartSyncResponseDto get(Long userId, String guestId);

    /**
     * Thêm vào giỏ (clearAll=false: cộng dồn số lượng) hoặc thay toàn bộ giỏ (clearAll=true). Số lượng bị
     * giới hạn theo tồn kho (available): vượt thì hạ về tối đa và trả success=false + reason/message; SKU
     * không tồn tại/không PUBLISHED hoặc hết hàng thì bỏ qua dòng đó. Phần còn lại của giỏ vẫn được lưu.
     * Khách vãng lai chưa có guestId -> server tạo mới và trả về trong response.
     */
    CartSyncResponseDto addOrSync(Long userId, String guestId, CartSyncRequestDto req);

    /**
     * Thêm nhiều SKU, giữ nguyên hàng đang có. Tất cả dòng phải bán được và đủ tồn; một dòng lỗi thì không ghi
     * dòng nào (400 CART_BATCH_FAILED). Không hạ số lượng như addOrSync.
     */
    CartSyncResponseDto batchAddStrict(Long userId, String guestId, CartSyncRequestDto req);

    /** Gộp giỏ vãng lai (guestId) vào giỏ tài khoản rồi xoá giỏ vãng lai. Bắt buộc đã đăng nhập. */
    CartSyncResponseDto merge(Long userId, String guestId);

    /** Tính tổng tiền cho danh sách dòng gửi lên (không đụng giỏ trong DB). */
    CartTotalResponseDto calculateTotal(CartTotalRequestDto req);

    /** Gợi ý sản phẩm cho giỏ (cùng danh mục với hàng trong giỏ lên trước, mới nhất trước), page 1-based. */
    CartRecommendationResponseDto recommend(Long userId, String guestId, int page, int take);

    /**
     * Đặt SỐ LƯỢNG CUỐI của một dòng (khác addOrSync: cộng dồn). quantity = 0 xoá dòng. Vượt tồn kho thì hạ về
     * tối đa và trả success=false + reason INSUFFICIENT_STOCK; SKU không còn bán thì bỏ qua (reason NOT_FOUND).
     */
    CartSyncResponseDto setQuantity(Long userId, String guestId, CartItemQuantityRequestDto req);

    /** Xoá hẳn một dòng (theo id SKU) khỏi giỏ; dòng không có trong giỏ thì bỏ qua. */
    CartSyncResponseDto removeItem(Long userId, String guestId, String variationId);
}
