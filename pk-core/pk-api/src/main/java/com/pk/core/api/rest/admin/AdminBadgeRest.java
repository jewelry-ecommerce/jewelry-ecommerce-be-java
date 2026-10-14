package com.pk.core.api.rest.admin;

import org.springframework.web.bind.annotation.RequestMapping;
import com.pk.core.model.constant.UrlConstant;
import lombok.RequiredArgsConstructor;

import com.pk.core.business.service.BadgeService;
import com.pk.core.business.web.AbstractRest;
import com.pk.core.common.web.BaseRes;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.model.dto.request.AdminBadgeFlowRequestDto;
import com.pk.core.model.dto.request.AdminBadgeTemplateRequestDto;
import com.pk.core.model.dto.response.MessageResponseDto;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API admin - Badge/nhãn dán sản phẩm (spec FE nhóm "Badge - Nhãn dán"). POST trả 200 và DELETE trả 200 +
 * {success, message} đúng spec. PUT flow nhận id trên path ({flowId}) vì spec không nói rõ sửa flow nào.
 */
@RestController
@RequestMapping(UrlConstant.Common.API + UrlConstant.Common.VERSION)
@RequiredArgsConstructor
public class AdminBadgeRest extends AbstractRest {

    private final BadgeService badgeService;

    // ---------- Badge templates ----------

    @GetMapping(UrlAdminConstant.Badge.TEMPLATES)
    public BaseRes listTemplates(@RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "20") int take,
                                  @RequestParam(required = false) String status,
                                  @RequestParam(required = false) String type,
                                  @RequestParam(required = false) String badgeType,
                                  @RequestParam(required = false) String defaultPosition,
                                  @RequestParam(required = false) String search,
                                  HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(
                    badgeService.findTemplates(page, take, status, type, badgeType, defaultPosition, search));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(UrlAdminConstant.Badge.TEMPLATES)
    public BaseRes createTemplate(
            @Validated(AdminBadgeTemplateRequestDto.OnCreate.class) @RequestBody AdminBadgeTemplateRequestDto request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(badgeService.createTemplate(request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @GetMapping(UrlAdminConstant.Badge.TEMPLATES + "/{badgeId}")
    public BaseRes templateDetail(@PathVariable String badgeId,
                                   HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(badgeService.findTemplateById(badgeId));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PatchMapping(UrlAdminConstant.Badge.TEMPLATES + "/{badgeId}")
    public BaseRes updateTemplate(@PathVariable String badgeId,
                                   @Valid @RequestBody AdminBadgeTemplateRequestDto request,
                                   HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(badgeService.updateTemplate(badgeId, request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @DeleteMapping(UrlAdminConstant.Badge.TEMPLATES + "/{badgeId}")
    public BaseRes deleteTemplate(@PathVariable String badgeId,
                                   HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            badgeService.deleteTemplate(badgeId);
            return restSuccessHandle.handleSuccess(MessageResponseDto.ok("Đã xóa mẫu nhãn."));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    // ---------- Badge flow ----------

    @GetMapping(UrlAdminConstant.Badge.FLOW)
    public BaseRes listFlows(@RequestParam(defaultValue = "1") int page,
                              @RequestParam(defaultValue = "20") int take,
                              @RequestParam(required = false) String status,
                              @RequestParam(required = false) String search,
                              @RequestParam(required = false) String displayStatus,
                              HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(badgeService.findFlows(page, take, status, search, displayStatus));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(UrlAdminConstant.Badge.FLOW)
    public BaseRes createFlow(@Valid @RequestBody AdminBadgeFlowRequestDto request,
                               HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(badgeService.createFlow(request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PutMapping(UrlAdminConstant.Badge.FLOW + "/{flowId}")
    public BaseRes updateFlow(@PathVariable String flowId, @Valid @RequestBody AdminBadgeFlowRequestDto request,
                               HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(badgeService.updateFlow(flowId, request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @DeleteMapping(UrlAdminConstant.Badge.FLOW + "/{flowId}")
    public BaseRes deleteFlow(@PathVariable String flowId,
                               HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            badgeService.deleteFlow(flowId);
            return restSuccessHandle.handleSuccess(MessageResponseDto.ok("Đã xóa luồng nhãn."));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }
}
