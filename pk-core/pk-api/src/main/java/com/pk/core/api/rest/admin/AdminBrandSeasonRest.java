package com.pk.core.api.rest.admin;

import com.pk.core.business.service.BrandService;
import com.pk.core.business.service.SeasonService;
import com.pk.core.business.web.AbstractRest;
import com.pk.core.common.web.BaseRes;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.model.dto.request.AdminBrandRequestDto;
import com.pk.core.model.dto.request.AdminSeasonRequestDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API admin - thương hiệu và mùa (V27). Quyền: ADMIN, CATALOG_MANAGER (nhóm /admin/catalog/**). */
@RestController
@RequestMapping(UrlConstant.Common.API + UrlConstant.Common.VERSION)
@RequiredArgsConstructor
public class AdminBrandSeasonRest extends AbstractRest {

    private final BrandService brandService;
    private final SeasonService seasonService;

    // ---------------- brands ----------------

    @GetMapping(UrlAdminConstant.Catalog.BRANDS)
    public BaseRes listBrands(@RequestParam(required = false) String search,
                              @RequestParam(required = false) String status,
                              @RequestParam(required = false, defaultValue = "1") int page,
                              @RequestParam(required = false, defaultValue = "20") int take,
                              HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(brandService.list(search, status, page, take));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @GetMapping(UrlAdminConstant.Catalog.BRANDS + "/{id}")
    public BaseRes brandDetail(@PathVariable Long id, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(brandService.detail(id));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(UrlAdminConstant.Catalog.BRANDS)
    @ResponseStatus(HttpStatus.CREATED)
    public BaseRes createBrand(@RequestBody AdminBrandRequestDto request,
                               HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(brandService.create(request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PatchMapping(UrlAdminConstant.Catalog.BRANDS + "/{id}")
    public BaseRes updateBrand(@PathVariable Long id, @RequestBody AdminBrandRequestDto request,
                               HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(brandService.update(id, request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @DeleteMapping(UrlAdminConstant.Catalog.BRANDS + "/{id}")
    public BaseRes deleteBrand(@PathVariable Long id, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            brandService.delete(id);
            return restSuccessHandle.handleSuccess(java.util.Map.of("success", true, "id", id));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    // ---------------- seasons ----------------

    @GetMapping(UrlAdminConstant.Catalog.SEASONS)
    public BaseRes listSeasons(@RequestParam(required = false) String search,
                               @RequestParam(required = false) Boolean isActive,
                               @RequestParam(required = false, defaultValue = "1") int page,
                               @RequestParam(required = false, defaultValue = "20") int take,
                               HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(seasonService.list(search, isActive, page, take));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @GetMapping(UrlAdminConstant.Catalog.SEASONS + "/{id}")
    public BaseRes seasonDetail(@PathVariable Long id, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(seasonService.detail(id));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(UrlAdminConstant.Catalog.SEASONS)
    @ResponseStatus(HttpStatus.CREATED)
    public BaseRes createSeason(@RequestBody AdminSeasonRequestDto request,
                                HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(seasonService.create(request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PatchMapping(UrlAdminConstant.Catalog.SEASONS + "/{id}")
    public BaseRes updateSeason(@PathVariable Long id, @RequestBody AdminSeasonRequestDto request,
                                HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(seasonService.update(id, request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @DeleteMapping(UrlAdminConstant.Catalog.SEASONS + "/{id}")
    public BaseRes deleteSeason(@PathVariable Long id, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            seasonService.delete(id);
            return restSuccessHandle.handleSuccess(java.util.Map.of("success", true, "id", id));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }
}
