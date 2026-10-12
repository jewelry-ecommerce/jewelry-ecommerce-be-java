package com.pk.core.business.repository;

import com.pk.core.model.entity.ProductSkuEntity;
import org.springframework.data.repository.query.Param;
import vn.com.unit.springframework.data.mirage.repository.query.Modifying;

import java.util.List;

public interface ProductSkuRepo extends PkRepo<ProductSkuEntity, Long> {

    List<ProductSkuEntity> findByProductId(@Param("productId") Long productId);

    /** Trả null nếu không có - dùng kiểm tra trùng `skuCode` lúc tạo sản phẩm+variants (409
     * DUPLICATE_SKU, Admin Catalog mục J). */
    ProductSkuEntity findBySkuCode(@Param("skuCode") String skuCode);

    /** Tìm SKU theo skuCode/tên (Admin Catalog mục J: GET /admin/catalog/variants) - lọc rỗng =
     * trả tất cả (cùng quy ước ProductRepo.searchAdmin, tránh IF/END động ở Mirage). */
    List<ProductSkuEntity> searchByKeyword(@Param("keyword") String keyword);

    /** Giữ chỗ `qty` đơn vị tồn kho (reserved += qty), CHỈ khi còn đủ hàng (on_hand - reserved >= qty)
     * - UPDATE có điều kiện nguyên tử, tránh race condition khi 2 đơn cùng đặt 1 SKU gần hết hàng
     * (không đọc-rồi-ghi bằng Java). Trả 0 nếu không đủ hàng -> OrderServiceImpl ném INSUFFICIENT_STOCK. */
    @Modifying
    int reserveStock(@Param("skuId") Long skuId, @Param("qty") int qty);

    /** Giữ chỗ cho ĐƠN ĐẶT TRƯỚC: chỉ khi SKU đang hết hàng (on_hand - reserved <= 0), tăng reserved bất chấp vượt
     * on_hand (available âm = số lượng đang nợ khách). Trả 0 nếu SKU vừa có hàng trở lại (gọi lại reserveStock). */
    @Modifying
    int reservePreOrderStock(@Param("skuId") Long skuId, @Param("qty") int qty);

    /** Nhả `qty` đơn vị đã giữ chỗ (reserved -= qty, không âm) - gọi khi huỷ đơn. */
    @Modifying
    int releaseStock(@Param("skuId") Long skuId, @Param("qty") int qty);

    /** Điều chỉnh tồn kho VẬT LÝ (on_hand) - Admin mục M (POST /admin/inventory/adjustments). `delta`
     * có thể âm (giảm, VD DAMAGE) hoặc dương (tăng, VD RECEIPT/CORRECTION). Chỉ áp dụng khi on_hand
     * sau điều chỉnh KHÔNG ÂM - UPDATE có điều kiện nguyên tử, cùng kiểu reserveStock/releaseStock
     * (tránh race condition 2 request điều chỉnh cùng lúc). Trả 0 nếu sẽ âm ->
     * InventoryServiceImpl ném 422 NEGATIVE_STOCK. */
    @Modifying
    int adjustOnHand(@Param("skuId") Long skuId, @Param("delta") int delta);

    /** Chốt xuất tồn khi giao: on_hand -= qty và reserved -= qty (đều không âm; đơn đặt trước có thể on_hand < qty). */
    @Modifying
    int fulfillStock(@Param("skuId") Long skuId, @Param("qty") int qty);
}
