package com.pk.core.api.rest.admin;

import com.pk.core.business.service.DashboardService;
import com.pk.core.business.web.AbstractRest;
import com.pk.core.common.web.BaseRes;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API admin - Dashboard (số liệu thật từ DB). Quyền: ADMIN, ORDER_MANAGER (xem SecurityConfig). */
@RestController
@RequestMapping(UrlConstant.Common.API + UrlConstant.Common.VERSION)
@RequiredArgsConstructor
public class AdminDashboardRest extends AbstractRest {

    private final DashboardService dashboardService;

    @GetMapping(UrlAdminConstant.Dashboard.OVERVIEW)
    public BaseRes overview(@RequestParam(required = false, defaultValue = "LAST_30_DAYS") String range,
                            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(dashboardService.overview(range));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @GetMapping(UrlAdminConstant.Dashboard.REVENUE)
    public BaseRes revenue(@RequestParam(required = false, defaultValue = "LAST_30_DAYS") String range,
                           HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(dashboardService.revenue(range));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @GetMapping(UrlAdminConstant.Dashboard.RECENT_ORDERS)
    public BaseRes recentOrders(@RequestParam(required = false, defaultValue = "8") int limit,
                                HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(dashboardService.recentOrders(limit));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @GetMapping(UrlAdminConstant.Dashboard.LOW_STOCK)
    public BaseRes lowStock(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(dashboardService.lowStock());
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @GetMapping(UrlAdminConstant.Dashboard.TOP_PRODUCTS)
    public BaseRes topProducts(@RequestParam(required = false, defaultValue = "LAST_30_DAYS") String range,
                               @RequestParam(required = false, defaultValue = "5") int limit,
                               HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(dashboardService.topProducts(range, limit));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }
}
