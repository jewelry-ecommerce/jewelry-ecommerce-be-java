package com.pk.core.business.repository;

import com.pk.core.model.entity.SearchHistoryEntity;
import org.springframework.data.repository.query.Param;
import vn.com.unit.springframework.data.mirage.repository.query.Modifying;

import java.util.List;

public interface SearchHistoryRepo extends PkRepo<SearchHistoryEntity, Long> {

    /** Đẩy từ khoá lên mới nhất. Trả 0 nếu người này chưa có từ khoá đó. */
    @Modifying
    int touch(@Param("userId") Long userId, @Param("keywordKey") String keywordKey, @Param("keyword") String keyword);

    /** Mới nhất trước. */
    List<SearchHistoryEntity> recent(@Param("userId") Long userId, @Param("pageSize") int pageSize);

    /** Giữ lại `keep` từ khoá gần nhất, xoá phần cũ hơn. */
    @Modifying
    int trimTo(@Param("userId") Long userId, @Param("keep") int keep);

    @Modifying
    int clearAll(@Param("userId") Long userId);

    @Modifying
    int removeOne(@Param("userId") Long userId, @Param("keywordKey") String keywordKey);
}
