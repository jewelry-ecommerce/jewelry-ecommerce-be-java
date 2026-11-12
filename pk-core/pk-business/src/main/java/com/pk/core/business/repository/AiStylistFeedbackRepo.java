package com.pk.core.business.repository;

import com.pk.core.model.entity.AiStylistFeedbackEntity;
import org.springframework.data.repository.query.Param;
import vn.com.unit.springframework.data.mirage.repository.query.Modifying;

import java.util.List;

public interface AiStylistFeedbackRepo extends PkRepo<AiStylistFeedbackEntity, Long> {

    /** Ghi đè liked nếu phiên đã có feedback cho sản phẩm này. */
    @Modifying
    int upsert(@Param("sessionId") Long sessionId, @Param("userId") Long userId,
               @Param("productId") Long productId, @Param("liked") boolean liked);

    /** Sản phẩm user đã đánh dấu không thích — loại khỏi lần gợi ý sau. */
    List<Long> findDislikedProductIds(@Param("userId") Long userId);
}
