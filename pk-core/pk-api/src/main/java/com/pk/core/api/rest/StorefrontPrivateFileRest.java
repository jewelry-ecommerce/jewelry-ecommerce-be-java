package com.pk.core.api.rest;

import com.pk.core.business.service.ImportExportService;
import com.pk.core.business.web.AbstractRest;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.dto.response.IeFileDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Tải file riêng tư bằng link ký có hạn (không cần Bearer để trình duyệt mở thẳng link). Hiện có CSV import/export:
 * GET /storefront/private-files/import-export/{jobId}?exp=...&sig=... - link do API job trả trong `fileUrl`.
 */
@RestController
@RequestMapping(UrlConstant.Common.API + UrlConstant.Common.VERSION)
@RequiredArgsConstructor
public class StorefrontPrivateFileRest extends AbstractRest {

    private final ImportExportService importExportService;

    @GetMapping(UrlConstant.Storefront.PRIVATE_FILES + "/import-export/{jobId}")
    public Object importExport(@PathVariable Long jobId, @RequestParam(required = false) Long exp,
                               @RequestParam(required = false) String sig,
                               HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            IeFileDto file = importExportService.downloadFile(jobId, exp, sig);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(file.fileName() == null ? "export.csv" : file.fileName(), StandardCharsets.UTF_8).build().toString())
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header("X-Content-Type-Options", "nosniff")
                    .body(file.content());
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }
}
