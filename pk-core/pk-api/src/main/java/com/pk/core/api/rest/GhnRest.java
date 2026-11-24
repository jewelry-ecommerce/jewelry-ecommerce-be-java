package com.pk.core.api.rest;

import com.pk.core.business.service.GhnShippingService;
import com.pk.core.business.web.AbstractRest;
import com.pk.core.common.web.BaseRes;
import com.pk.core.model.constant.UrlConstant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Tính phí GHN staging cho checkout/admin demo. */
@RestController
@RequestMapping(UrlConstant.Common.API + UrlConstant.Common.VERSION)
@RequiredArgsConstructor
public class GhnRest extends AbstractRest {

    private final GhnShippingService shipping;

    @PostMapping(UrlConstant.Ghn.FEE)
    public BaseRes fee(@RequestBody(required = false) Map<String, Object> body,
                       HttpServletRequest rq, HttpServletResponse rs) {
        try {
            Integer district = body == null || body.get("toDistrictId") == null ? null
                    : Integer.valueOf(String.valueOf(body.get("toDistrictId")));
            String ward = body == null || body.get("toWardCode") == null ? null : String.valueOf(body.get("toWardCode"));
            Integer weight = body == null || body.get("weightGrams") == null ? null
                    : Integer.valueOf(String.valueOf(body.get("weightGrams")));
            return restSuccessHandle.handleSuccess(shipping.quoteFee(district, ward, weight));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, rq, rs);
        }
    }
}
