package com.pk.core.api.rest.admin;

import com.pk.core.business.service.ProductReviewService;
import com.pk.core.business.web.AbstractRest;
import com.pk.core.common.web.BaseRes;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Admin duyệt đánh giá sản phẩm. */
@RestController
@RequestMapping(UrlConstant.Common.API + UrlConstant.Common.VERSION)
@RequiredArgsConstructor
public class AdminProductReviewRest extends AbstractRest {

    private static final String BASE = UrlAdminConstant.ProductReview.BASE;

    private final ProductReviewService productReviewService;

    @GetMapping(BASE)
    public BaseRes list(@RequestParam(required = false) String status,
                        HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(productReviewService.adminList(status));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(BASE + "/{id}/approve")
    public BaseRes approve(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body,
                           HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            String reply = body == null ? null : body.get("reply");
            return restSuccessHandle.handleSuccess(productReviewService.adminApprove(id, reply));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(BASE + "/{id}/reject")
    public BaseRes reject(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body,
                          HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            String reason = body == null ? null : body.get("reason");
            return restSuccessHandle.handleSuccess(productReviewService.adminReject(id, reason));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(BASE + "/{id}/reply")
    public BaseRes reply(@PathVariable Long id, @RequestBody Map<String, String> body,
                         HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(productReviewService.adminReply(id, body == null ? null : body.get("reply")));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }
}
