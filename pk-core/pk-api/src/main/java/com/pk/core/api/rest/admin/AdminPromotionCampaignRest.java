package com.pk.core.api.rest.admin;

import com.pk.core.business.service.PromotionCampaignService;
import com.pk.core.business.web.AbstractRest;
import com.pk.core.common.web.BaseRes;
import com.pk.core.identity.security.model.SecurityUser;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * API admin - wizard chiến dịch khuyến mãi (nháp -> hình thức -> thành phần -> voucher -> phát hành -> vòng đời).
 * Quyền: nhóm /admin/promotions/** (ADMIN, CATALOG_MANAGER). Tiền tố FE -> BE: promotion/promotion-drafts -> admin/promotions/drafts,
 * promotion/promotions -> admin/promotions/published, promotion/promotion-campaigns -> admin/promotions/campaigns.
 */
@RestController
@RequestMapping(UrlConstant.Common.API + UrlConstant.Common.VERSION)
@RequiredArgsConstructor
public class AdminPromotionCampaignRest extends AbstractRest {

    private static final String D = UrlAdminConstant.Promotion.DRAFTS;
    private static final String P = UrlAdminConstant.Promotion.PUBLISHED;

    private final PromotionCampaignService service;

    private BaseRes run(Supplier<Object> action, HttpServletRequest rq, HttpServletResponse rs) {
        try {
            return restSuccessHandle.handleSuccess(action.get());
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, rq, rs);
        }
    }

    private static Long actor(SecurityUser u) {
        return u == null ? null : u.getId();
    }

    // ---------------------------------------------------------------- tổng quan
    @GetMapping(UrlAdminConstant.Promotion.CAMPAIGNS)
    public BaseRes overview(@RequestParam Map<String, String> params, HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.overview(new HashMap<>(params)), rq, rs);
    }

    // ---------------------------------------------------------------- nháp
    @GetMapping(D)
    public BaseRes listDrafts(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer limit,
                              @RequestParam(required = false) Integer take, @RequestParam(required = false) String status,
                              @RequestParam(required = false) String search, HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.listDrafts(page, limit != null ? limit : take, status, search), rq, rs);
    }

    @PostMapping(D)
    @ResponseStatus(HttpStatus.CREATED)
    public BaseRes createDraft(@RequestBody Map<String, Object> body, @AuthenticationPrincipal SecurityUser u, HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.createDraft(body, actor(u)), rq, rs);
    }

    @GetMapping(D + "/{draftId}")
    public BaseRes getDraft(@PathVariable String draftId, HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.getDraft(draftId), rq, rs);
    }

    @PatchMapping(D + "/{draftId}")
    public BaseRes updateDraft(@PathVariable String draftId, @RequestBody Map<String, Object> body, @AuthenticationPrincipal SecurityUser u,
                               HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.updateDraft(draftId, body, actor(u)), rq, rs);
    }

    @DeleteMapping(D + "/{draftId}")
    public BaseRes deleteDraft(@PathVariable String draftId, @AuthenticationPrincipal SecurityUser u, HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> {
            service.deleteDraft(draftId, actor(u));
            return Map.of("success", true);
        }, rq, rs);
    }

    @PostMapping(D + "/{draftId}/publish")
    public BaseRes publish(@PathVariable String draftId, @AuthenticationPrincipal SecurityUser u, HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.publishDraft(draftId, actor(u)), rq, rs);
    }

    // ---------------------------------------------------------------- thành phần cấp chiến dịch
    @GetMapping(D + "/{draftId}/components")
    public BaseRes components(@PathVariable String draftId, HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.draftComponents(draftId), rq, rs);
    }

    @PutMapping(D + "/{draftId}/target")
    public BaseRes target(@PathVariable String draftId, @RequestBody Map<String, Object> body, @AuthenticationPrincipal SecurityUser u,
                          HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.upsertDraftComponent(draftId, "TARGET", body, actor(u)), rq, rs);
    }

    @PutMapping(D + "/{draftId}/audience")
    public BaseRes audience(@PathVariable String draftId, @RequestBody Map<String, Object> body, @AuthenticationPrincipal SecurityUser u,
                            HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.upsertDraftComponent(draftId, "AUDIENCE", body, actor(u)), rq, rs);
    }

    @PutMapping(D + "/{draftId}/applicability")
    public BaseRes applicability(@PathVariable String draftId, @RequestBody Map<String, Object> body, @AuthenticationPrincipal SecurityUser u,
                                 HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.upsertDraftComponent(draftId, "APPLICABILITY", body, actor(u)), rq, rs);
    }

    // ---------------------------------------------------------------- schema (route cố định /order đứng trước {schemaId})
    @GetMapping(D + "/{draftId}/schemas")
    public BaseRes schemas(@PathVariable String draftId, HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.listSchemas(draftId), rq, rs);
    }

    @PostMapping(D + "/{draftId}/schemas")
    @ResponseStatus(HttpStatus.CREATED)
    public BaseRes createSchema(@PathVariable String draftId, @RequestBody Map<String, Object> body, @AuthenticationPrincipal SecurityUser u,
                                HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.createSchema(draftId, body, actor(u)), rq, rs);
    }

    @PutMapping(D + "/{draftId}/schemas/order")
    public BaseRes reorder(@PathVariable String draftId, @RequestBody Map<String, Object> body, @AuthenticationPrincipal SecurityUser u,
                           HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> {
            Object ids = body.get("schemaIds");
            List<String> list = ids instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of();
            service.reorderSchemas(draftId, list, actor(u));
            return Map.of("success", true);
        }, rq, rs);
    }

    @GetMapping(D + "/{draftId}/schemas/{schemaId}")
    public BaseRes schema(@PathVariable String draftId, @PathVariable String schemaId, HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.getSchema(draftId, schemaId), rq, rs);
    }

    @PatchMapping(D + "/{draftId}/schemas/{schemaId}")
    public BaseRes updateSchema(@PathVariable String draftId, @PathVariable String schemaId, @RequestBody Map<String, Object> body,
                                @AuthenticationPrincipal SecurityUser u, HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.updateSchema(draftId, schemaId, body, actor(u)), rq, rs);
    }

    @DeleteMapping(D + "/{draftId}/schemas/{schemaId}")
    public BaseRes deleteSchema(@PathVariable String draftId, @PathVariable String schemaId, HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> {
            service.deleteSchema(draftId, schemaId);
            return Map.of("success", true);
        }, rq, rs);
    }

    @GetMapping(D + "/{draftId}/schemas/{schemaId}/components")
    public BaseRes schemaComponents(@PathVariable String draftId, @PathVariable String schemaId, HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.schemaComponents(draftId, schemaId), rq, rs);
    }

    @GetMapping(D + "/{draftId}/schemas/{schemaId}/condition-config-options")
    public BaseRes conditionOptions(@PathVariable String draftId, @PathVariable String schemaId, HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.componentOptions(draftId, schemaId, "CONDITION"), rq, rs);
    }

    @GetMapping(D + "/{draftId}/schemas/{schemaId}/benefit-config-options")
    public BaseRes benefitOptions(@PathVariable String draftId, @PathVariable String schemaId, HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.componentOptions(draftId, schemaId, "BENEFIT"), rq, rs);
    }

    @PutMapping(D + "/{draftId}/schemas/{schemaId}/condition")
    public BaseRes condition(@PathVariable String draftId, @PathVariable String schemaId, @RequestBody Map<String, Object> body,
                             @AuthenticationPrincipal SecurityUser u, HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.upsertSchemaComponent(draftId, schemaId, "CONDITION", body, actor(u)), rq, rs);
    }

    @PutMapping(D + "/{draftId}/schemas/{schemaId}/benefit")
    public BaseRes benefit(@PathVariable String draftId, @PathVariable String schemaId, @RequestBody Map<String, Object> body,
                           @AuthenticationPrincipal SecurityUser u, HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.upsertSchemaComponent(draftId, schemaId, "BENEFIT", body, actor(u)), rq, rs);
    }

    // ---------------------------------------------------------------- mã voucher
    @GetMapping(D + "/{draftId}/schemas/{schemaId}/voucher-code-authoring")
    public BaseRes voucherAuthoring(@PathVariable String draftId, @PathVariable String schemaId, HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.voucherAuthoring(draftId, schemaId), rq, rs);
    }

    @GetMapping(D + "/{draftId}/schemas/{schemaId}/voucher-codes")
    public BaseRes voucherCodes(@PathVariable String draftId, @PathVariable String schemaId, HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.voucherCodes(draftId, schemaId), rq, rs);
    }

    @PutMapping(D + "/{draftId}/schemas/{schemaId}/voucher-codes")
    public BaseRes upsertVoucherCode(@PathVariable String draftId, @PathVariable String schemaId, @RequestBody Map<String, Object> body,
                                     @AuthenticationPrincipal SecurityUser u, HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.upsertVoucherCode(draftId, schemaId, body, actor(u)), rq, rs);
    }

    @DeleteMapping(D + "/{draftId}/schemas/{schemaId}/voucher-codes/{voucherCodeId}")
    public BaseRes deleteVoucherCode(@PathVariable String draftId, @PathVariable String schemaId, @PathVariable String voucherCodeId,
                                     HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> {
            service.deleteVoucherCode(draftId, schemaId, voucherCodeId);
            return Map.of("success", true);
        }, rq, rs);
    }

    // ---------------------------------------------------------------- bản đã phát hành
    @GetMapping(P + "/{promotionId}")
    public BaseRes publishedDetail(@PathVariable String promotionId, HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.publishedDetail(promotionId), rq, rs);
    }

    @GetMapping(P + "/{promotionId}/quota-statistics")
    public BaseRes quota(@PathVariable String promotionId, HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.publishedQuotaStatistics(promotionId), rq, rs);
    }

    @GetMapping(P + "/{promotionId}/components")
    public BaseRes publishedComponents(@PathVariable String promotionId, HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.publishedComponents(promotionId), rq, rs);
    }

    @GetMapping(P + "/{promotionId}/schemas")
    public BaseRes publishedSchemas(@PathVariable String promotionId, HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.publishedSchemas(promotionId), rq, rs);
    }

    @GetMapping(P + "/{promotionId}/schemas/{schemaId}/components")
    public BaseRes publishedSchemaComponents(@PathVariable String promotionId, @PathVariable String schemaId, HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.publishedSchemaComponents(promotionId, schemaId), rq, rs);
    }

    @GetMapping(P + "/{promotionId}/schemas/{schemaId}/voucher-codes")
    public BaseRes publishedVoucherCodes(@PathVariable String promotionId, @PathVariable String schemaId, HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.publishedVoucherCodes(promotionId, schemaId), rq, rs);
    }

    @PostMapping(P + "/{promotionId}/pause")
    public BaseRes pause(@PathVariable String promotionId, @AuthenticationPrincipal SecurityUser u, HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.pause(promotionId, actor(u)), rq, rs);
    }

    @PostMapping(P + "/{promotionId}/resume")
    public BaseRes resume(@PathVariable String promotionId, @AuthenticationPrincipal SecurityUser u, HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.resume(promotionId, actor(u)), rq, rs);
    }

    @PostMapping(P + "/{promotionId}/end")
    public BaseRes end(@PathVariable String promotionId, @AuthenticationPrincipal SecurityUser u, HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.end(promotionId, actor(u)), rq, rs);
    }

    @PostMapping(P + "/{promotionId}/schemas/{schemaId}/unpublish")
    public BaseRes unpublishSchema(@PathVariable String promotionId, @PathVariable String schemaId, @AuthenticationPrincipal SecurityUser u,
                                   HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.unpublishSchema(promotionId, schemaId, actor(u)), rq, rs);
    }
}
