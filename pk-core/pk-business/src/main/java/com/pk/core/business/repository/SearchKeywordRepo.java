package com.pk.core.business.repository;

import com.pk.core.model.entity.SearchKeywordEntity;
import org.springframework.data.repository.query.Param;
import vn.com.unit.springframework.data.mirage.repository.query.Modifying;

import java.util.Date;
import java.util.List;

public interface SearchKeywordRepo extends PkRepo<SearchKeywordEntity, Long> {

    /** Tăng lượt tìm của từ khoá (theo keywordKey). Trả 0 nếu chưa có dòng. */
    @Modifying
    int bump(@Param("keywordKey") String keywordKey);

    /** Đổi dạng hiển thị (vd từ "nhan" sang "nhẫn" khi có người gõ đủ dấu). */
    @Modifying
    int rename(@Param("keywordKey") String keywordKey, @Param("keyword") String keyword);

    /** Từ khoá được tìm nhiều nhất kể từ `since`. */
    List<SearchKeywordEntity> trending(@Param("since") Date since, @Param("pageSize") int pageSize);

    /** Từ khoá có keywordKey khớp mẫu LIKE (escape '!'), nhiều lượt tìm trước. */
    List<SearchKeywordEntity> suggest(@Param("pattern") String pattern, @Param("pageSize") int pageSize);
}
