package com.pk.core.api.rest;

import com.pk.core.business.service.SearchKeywordService;
import com.pk.core.business.web.AbstractRest;
import com.pk.core.common.web.BaseRes;
import com.pk.core.identity.security.model.SecurityUser;
import com.pk.core.model.constant.UrlConstant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Ô tìm kiếm storefront (client): từ khoá phổ biến, gợi ý (công khai) và lịch sử của tài khoản (cần đăng nhập - SecurityConfig).
 * Dữ liệu thật từ DB (V38): từ khoá chỉ được ghi nhận khi một lượt tìm sản phẩm có kết quả (xem ProductRest.search).
 */
@RestController
@RequestMapping(UrlConstant.Common.API + UrlConstant.Common.VERSION)
@RequiredArgsConstructor
public class SearchRest extends AbstractRest {

    private final SearchKeywordService searchService;

    @GetMapping(UrlConstant.Product.SEARCH + "/trending")
    public BaseRes trending(@RequestParam(required = false) Integer limit,
                            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(searchService.trending(limit));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @GetMapping(UrlConstant.Product.SEARCH + "/suggestions")
    public BaseRes suggestions(@RequestParam(required = false) String q,
                               HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(searchService.suggestions(q));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @GetMapping(UrlConstant.Product.SEARCH + "/history")
    public BaseRes history(@AuthenticationPrincipal SecurityUser principal, @RequestParam(required = false) Integer limit,
                           HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(searchService.history(principal.getId(), limit));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @DeleteMapping(UrlConstant.Product.SEARCH + "/history")
    public BaseRes clearHistory(@AuthenticationPrincipal SecurityUser principal,
                                HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            searchService.clearHistory(principal.getId());
            return restSuccessHandle.handleSuccess(Map.of("success", true));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    /** Xoá một từ khoá khỏi lịch sử. Có hai dạng: path (/history/keyword/{keyword}) và query (/history/keyword?keyword=...). */
    @DeleteMapping(UrlConstant.Product.SEARCH + "/history/keyword/{keyword}")
    public BaseRes removeKeyword(@AuthenticationPrincipal SecurityUser principal, @PathVariable String keyword,
                                 HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        return removeKeywordImpl(principal, keyword, httpRequest, httpResponse);
    }

    /** Dạng query: dùng được với từ khoá có dấu/khoảng trắng qua BFF của client (BFF chỉ cho phép ký tự ASCII trong path). */
    @DeleteMapping(UrlConstant.Product.SEARCH + "/history/keyword")
    public BaseRes removeKeywordByQuery(@AuthenticationPrincipal SecurityUser principal,
                                        @RequestParam(required = false) String keyword,
                                        HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        return removeKeywordImpl(principal, keyword, httpRequest, httpResponse);
    }

    private BaseRes removeKeywordImpl(SecurityUser principal, String keyword, HttpServletRequest httpRequest,
                                      HttpServletResponse httpResponse) {
        try {
            searchService.removeHistoryKeyword(principal.getId(), keyword);
            return restSuccessHandle.handleSuccess(Map.of("success", true));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }
}
