package com.pk.core.api.rest.admin;

import com.pk.core.business.service.PromotionRuntimeService;
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
import java.util.function.Supplier;

/**
 * API admin - chạy thử khuyến mãi (sandbox), tra cứu/kiểm tra voucher, tra khách theo SĐT.
 * Tiền tố FE -> BE: promotion/internal/promotion-runtime/evaluate -> admin/promotions/runtime/evaluate;
 * promotion/promotion-vouchers/{discover,validate-code} -> admin/promotions/vouchers/... (hoặc admin/orders/promotion-vouchers/... cho ORDER_MANAGER);
 * promotion/admin/customer-audience/by-phone -> admin/promotions/customer-audience/by-phone.
 */
@RestController
@RequestMapping(UrlConstant.Common.API + UrlConstant.Common.VERSION)
@RequiredArgsConstructor
public class AdminPromotionRuntimeRest extends AbstractRest {

    private final PromotionRuntimeService service;

    private BaseRes run(Supplier<Object> action, HttpServletRequest rq, HttpServletResponse rs) {
        try {
            return restSuccessHandle.handleSuccess(action.get());
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, rq, rs);
        }
    }

    @PostMapping(UrlAdminConstant.Promotion.RUNTIME_EVALUATE)
    public BaseRes evaluate(@RequestBody Map<String, Object> body, HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.evaluate(body), rq, rs);
    }

    @PostMapping(UrlAdminConstant.Promotion.DRAFTS + "/{draftId}/simulate")
    public BaseRes simulate(@PathVariable String draftId, @RequestBody Map<String, Object> body, HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.simulate(draftId, body), rq, rs);
    }

    @PostMapping(UrlAdminConstant.Promotion.DRAFTS + "/{draftId}/simulation-vouchers/discover")
    public BaseRes simulationVouchers(@PathVariable String draftId, @RequestBody Map<String, Object> body, HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.simulationVouchers(draftId, body), rq, rs);
    }

    @PostMapping({UrlAdminConstant.Promotion.VOUCHER_DISCOVER, UrlAdminConstant.Promotion.ORDER_VOUCHER_DISCOVER})
    public BaseRes discover(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.discoverVouchers(body == null ? Map.of() : body), rq, rs);
    }

    @PostMapping({UrlAdminConstant.Promotion.VOUCHER_VALIDATE, UrlAdminConstant.Promotion.ORDER_VOUCHER_VALIDATE})
    public BaseRes validate(@RequestBody Map<String, Object> body, HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.validateCode(body), rq, rs);
    }

    @GetMapping(UrlAdminConstant.Promotion.CUSTOMER_BY_PHONE)
    public BaseRes customerByPhone(@RequestParam String phone, HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.customerByPhone(phone), rq, rs);
    }
}
