package com.pk.core.api.rest.admin;

import com.pk.core.business.service.ImportExportService;
import com.pk.core.business.web.AbstractRest;
import com.pk.core.common.web.BaseRes;
import com.pk.core.identity.security.model.SecurityUser;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.model.dto.request.IeProductExportRequestDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Import/export CSV sản phẩm + tra cứu job (V24). Chạy đồng bộ: POST trả luôn job DONE/FAILED kèm fileUrl. */
@RestController
@RequestMapping(UrlConstant.Common.API + UrlConstant.Common.VERSION)
@RequiredArgsConstructor
public class AdminImportExportRest extends AbstractRest {

    private final ImportExportService service;

    @GetMapping(UrlAdminConstant.Ie.TOPICS)
    public BaseRes topics(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(service.topics());
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(UrlAdminConstant.Ie.EXPORT_PRODUCTS)
    public BaseRes exportProducts(@RequestBody(required = false) IeProductExportRequestDto request,
                                  @AuthenticationPrincipal SecurityUser principal,
                                  HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(service.exportProducts(request, principal == null ? null : principal.getId()));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(value = UrlAdminConstant.Ie.IMPORT_PRODUCTS, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseRes importProducts(@RequestPart("file") MultipartFile file,
                                  @RequestParam(required = false) String referenceType,
                                  @RequestParam(required = false) String referenceId,
                                  @AuthenticationPrincipal SecurityUser principal,
                                  HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(service.importProducts(file, referenceType, referenceId,
                    principal == null ? null : principal.getId()));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @GetMapping(UrlAdminConstant.Ie.JOBS)
    public BaseRes jobs(@RequestParam(required = false) String jobType, @RequestParam(required = false) String status,
                        @RequestParam(required = false, defaultValue = "1") int page,
                        @RequestParam(required = false, defaultValue = "20") int take,
                        HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(service.listJobs(jobType, status, page, take));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @GetMapping(UrlAdminConstant.Ie.JOB_STATUS + "/{jobId}")
    public BaseRes jobStatus(@PathVariable Long jobId, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(service.getJob(jobId));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @GetMapping(UrlAdminConstant.Ie.JOBS + "/{jobId}/result-rows")
    public BaseRes resultRows(@PathVariable Long jobId, @RequestParam(required = false) String status,
                              @RequestParam(required = false, defaultValue = "1") int page,
                              @RequestParam(required = false, defaultValue = "20") int take,
                              HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(service.resultRows(jobId, status, page, take));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }
}
