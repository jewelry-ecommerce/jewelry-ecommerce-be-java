package com.pk.core.business.service;

import com.pk.core.model.dto.response.SearchHistoryItemResponseDto;
import com.pk.core.model.dto.response.SearchSuggestionResponseDto;

import java.util.List;

/**
 * Tìm kiếm storefront: từ khoá phổ biến, gợi ý và lịch sử theo tài khoản (V38). Không có dữ liệu giả: chưa ai tìm thì trending rỗng.
 */
public interface SearchKeywordService {

    /** Ghi nhận một lượt tìm CÓ KẾT QUẢ. userId null = khách vãng lai (chỉ góp vào trending/gợi ý, không có lịch sử). */
    void record(Long userId, String rawKeyword);

    /** Từ khoá được tìm nhiều trong 30 ngày gần đây; limit mặc định 10, tối đa 20. */
    List<String> trending(Integer limit);

    /** Gợi ý theo `q` (không phân biệt dấu): tối đa 3 danh mục + từ khoá phổ biến, tổng tối đa 8. q ngắn hơn 2 ký tự -> rỗng. */
    List<SearchSuggestionResponseDto> suggestions(String q);

    /** Lịch sử gần nhất của tài khoản, mới trước; limit mặc định 10, tối đa 20. */
    List<SearchHistoryItemResponseDto> history(Long userId, Integer limit);

    void clearHistory(Long userId);

    /** Xoá một từ khoá khỏi lịch sử (không phân biệt dấu/hoa thường); không có thì bỏ qua. */
    void removeHistoryKeyword(Long userId, String keyword);
}
