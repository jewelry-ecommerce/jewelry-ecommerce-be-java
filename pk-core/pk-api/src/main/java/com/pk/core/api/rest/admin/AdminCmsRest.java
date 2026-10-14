package com.pk.core.api.rest.admin;

import org.springframework.web.bind.annotation.RequestMapping;
import com.pk.core.model.constant.UrlConstant;
import lombok.RequiredArgsConstructor;

import com.pk.core.business.service.CmsService;
import com.pk.core.business.web.AbstractRest;
import com.pk.core.common.web.BaseRes;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.model.dto.request.AdminCmsBlockRequestDto;
import com.pk.core.model.dto.request.AdminCmsPageRequestDto;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API admin - CMS (document/09-tong-hop-api-fe.md mục O). Gói api.rest.admin (pk-api). */
@RestController
@RequestMapping(UrlConstant.Common.API + UrlConstant.Common.VERSION)
@RequiredArgsConstructor
public class AdminCmsRest extends AbstractRest {

    private final CmsService cmsService;

    @GetMapping(UrlAdminConstant.Cms.PAGES)
    public BaseRes listPages(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(cmsService.findAllPages());
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(UrlAdminConstant.Cms.PAGES)
    @ResponseStatus(HttpStatus.CREATED)
    public BaseRes createPage(@Valid @RequestBody AdminCmsPageRequestDto request,
                               HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(cmsService.createPage(request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @GetMapping(UrlAdminConstant.Cms.PAGES + "/{pageId}")
    public BaseRes pageDetail(@PathVariable Long pageId,
                               HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(cmsService.findPageById(pageId));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PutMapping(UrlAdminConstant.Cms.PAGES + "/{pageId}")
    public BaseRes updatePage(@PathVariable Long pageId, @Valid @RequestBody AdminCmsPageRequestDto request,
                               HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(cmsService.updatePage(pageId, request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(UrlAdminConstant.Cms.PAGES + "/{pageId}/publish")
    public BaseRes publishPage(@PathVariable Long pageId,
                                HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(cmsService.publishPage(pageId));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(UrlAdminConstant.Cms.PAGES + "/{pageId}/unpublish")
    public BaseRes unpublishPage(@PathVariable Long pageId,
                                  HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(cmsService.unpublishPage(pageId));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @DeleteMapping(UrlAdminConstant.Cms.PAGES + "/{pageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePage(@PathVariable Long pageId) {
        cmsService.deletePage(pageId);
    }

    @PostMapping(UrlAdminConstant.Cms.PAGES + "/{pageId}/blocks")
    @ResponseStatus(HttpStatus.CREATED)
    public BaseRes addBlock(@PathVariable Long pageId, @Valid @RequestBody AdminCmsBlockRequestDto request,
                             HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(cmsService.addBlock(pageId, request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PutMapping(UrlAdminConstant.Cms.PAGES + "/{pageId}/blocks/{blockId}")
    public BaseRes updateBlock(@PathVariable Long pageId, @PathVariable Long blockId,
                                @Valid @RequestBody AdminCmsBlockRequestDto request,
                                HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(cmsService.updateBlock(pageId, blockId, request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @DeleteMapping(UrlAdminConstant.Cms.PAGES + "/{pageId}/blocks/{blockId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBlock(@PathVariable Long pageId, @PathVariable Long blockId) {
        cmsService.deleteBlock(pageId, blockId);
    }
}
