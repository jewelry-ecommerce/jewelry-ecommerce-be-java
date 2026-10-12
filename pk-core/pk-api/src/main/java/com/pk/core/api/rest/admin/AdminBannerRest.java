package com.pk.core.api.rest.admin;

import org.springframework.web.bind.annotation.RequestMapping;
import com.pk.core.model.constant.UrlConstant;
import lombok.RequiredArgsConstructor;

import com.pk.core.business.web.AbstractRest;
import com.pk.core.common.web.BaseRes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.pk.core.business.service.BannerLayoutService;
import com.pk.core.business.service.BannerPlacementService;
import com.pk.core.model.dto.request.AdminBannerLayoutItemRequestDto;
import com.pk.core.model.dto.request.AdminBannerLayoutRequestDto;
import com.pk.core.business.service.BannerService;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.model.dto.request.AdminBannerPlacementRequestDto;
import com.pk.core.model.dto.request.AdminBannerRequestDto;
import com.pk.core.model.dto.response.MessageResponseDto;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

/**
 * API admin - Banner (spec FE nhóm Banner). Gói api.rest.admin (pk-api). Theo spec: POST trả 200 + banner vừa tạo (không phải 201),
 * DELETE trả 200 + {success, message} (khác quy ước 204 của các Rest admin cũ vì spec FE quy định như vậy).
 */
@RestController
@RequestMapping(UrlConstant.Common.API + UrlConstant.Common.VERSION)
@RequiredArgsConstructor
public class AdminBannerRest extends AbstractRest {

    private final BannerService bannerService;
    private final BannerPlacementService placementService;
    private final BannerLayoutService layoutService;

    @GetMapping(UrlAdminConstant.Banner.BANNERS)
    public BaseRes list(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int take,
                        @RequestParam(required = false) String status,
                        @RequestParam(required = false) String mediaType,
                        @RequestParam(required = false) String actionType,
                        @RequestParam(required = false) String internalName,
                        HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(
                    bannerService.findAll(page, take, status, mediaType, actionType, internalName));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(UrlAdminConstant.Banner.BANNERS)
    public BaseRes create(@Validated(AdminBannerRequestDto.OnCreate.class) @RequestBody AdminBannerRequestDto request,
                          HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(bannerService.create(request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @GetMapping(UrlAdminConstant.Banner.BANNERS + "/{id}")
    public BaseRes detail(@PathVariable Long id, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(bannerService.findById(id));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PatchMapping(UrlAdminConstant.Banner.BANNERS + "/{id}")
    public BaseRes update(@PathVariable Long id, @Valid @RequestBody AdminBannerRequestDto request,
                          HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(bannerService.update(id, request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @DeleteMapping(UrlAdminConstant.Banner.BANNERS + "/{id}")
    public BaseRes delete(@PathVariable Long id, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            bannerService.delete(id);
            return restSuccessHandle.handleSuccess(MessageResponseDto.ok("Đã xoá banner"));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    // ---------- Vị trí banner (placements) ----------

    @GetMapping(UrlAdminConstant.Banner.PLACEMENTS)
    public BaseRes listPlacements(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(placementService.findAll());
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(UrlAdminConstant.Banner.PLACEMENTS)
    public BaseRes createPlacement(@Valid @RequestBody AdminBannerPlacementRequestDto request,
                                   HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(placementService.create(request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PatchMapping(UrlAdminConstant.Banner.PLACEMENTS + "/{id}")
    public BaseRes updatePlacement(@PathVariable Long id, @Valid @RequestBody AdminBannerPlacementRequestDto request,
                                   HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(placementService.update(id, request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    // ---------- Banner builder: layout của vị trí (slot / toạ độ / thứ tự) ----------

    @GetMapping(UrlAdminConstant.Banner.PLACEMENTS + "/{id}/layouts")
    public BaseRes listLayouts(@PathVariable Long id, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(layoutService.list(id));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @GetMapping(UrlAdminConstant.Banner.PLACEMENTS + "/{id}/layouts/{layoutId}")
    public BaseRes layoutDetail(@PathVariable Long id, @PathVariable Long layoutId,
                                HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(layoutService.detail(id, layoutId));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(UrlAdminConstant.Banner.PLACEMENTS + "/{id}/layouts")
    public BaseRes createLayout(@PathVariable Long id, @Valid @RequestBody AdminBannerLayoutRequestDto request,
                                HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(layoutService.create(id, request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PatchMapping(UrlAdminConstant.Banner.PLACEMENTS + "/{id}/layouts/{layoutId}")
    public BaseRes updateLayout(@PathVariable Long id, @PathVariable Long layoutId,
                                @Valid @RequestBody AdminBannerLayoutRequestDto request,
                                HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(layoutService.update(id, layoutId, request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PatchMapping(UrlAdminConstant.Banner.PLACEMENTS + "/{id}/layouts/{layoutId}/items/{itemId}")
    public BaseRes patchLayoutItem(@PathVariable Long id, @PathVariable Long layoutId, @PathVariable Long itemId,
                                   @RequestBody AdminBannerLayoutItemRequestDto request,
                                   HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(layoutService.patchItem(id, layoutId, itemId, request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @DeleteMapping(UrlAdminConstant.Banner.PLACEMENTS + "/{id}/layouts/{layoutId}/items/{itemId}")
    public BaseRes deleteLayoutItem(@PathVariable Long id, @PathVariable Long layoutId, @PathVariable Long itemId,
                                    HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            layoutService.deleteItem(id, layoutId, itemId);
            return restSuccessHandle.handleSuccess(MessageResponseDto.ok("Đã gỡ banner khỏi slot (banner vẫn còn)"));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }
}
