package com.pk.core.business.repository;

import com.pk.core.model.entity.InventoryMovementEntity;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;

public interface InventoryMovementRepo extends PkRepo<InventoryMovementEntity, Long> {

    /** Dòng sổ đã ghi với (người làm, Idempotency-Key); null nếu chưa có. */
    InventoryMovementEntity findByActorAndKey(@Param("createdId") Long createdId, @Param("idemKey") String idemKey);

    /** Lịch sử mới nhất trước. skuId = 0 và reason = "" là "không lọc"; khoảng thời gian [fromAt, toAt) luôn có giá trị. */
    List<InventoryMovementEntity> search(@Param("skuId") Long skuId, @Param("reason") String reason,
                                         @Param("fromAt") Date fromAt, @Param("toAt") Date toAt,
                                         @Param("pageSize") int pageSize, @Param("pageOffset") int pageOffset);

    /** Tổng số dòng khớp cùng bộ lọc của {@link #search}. */
    Long countSearch(@Param("skuId") Long skuId, @Param("reason") String reason,
                     @Param("fromAt") Date fromAt, @Param("toAt") Date toAt);
}
