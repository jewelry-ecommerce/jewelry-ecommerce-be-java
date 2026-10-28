package com.pk.core.business.service;

import com.pk.core.common.web.PageResponse;
import com.pk.core.model.dto.request.IeProductExportRequestDto;
import com.pk.core.model.dto.response.IeFileDto;
import com.pk.core.model.dto.response.IeJobResponseDto;
import com.pk.core.model.dto.response.IeResultRowDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/** Import/export CSV sản phẩm + quản lý job (V24). Chạy đồng bộ; job lưu lại để tra cứu. */
public interface ImportExportService {

    /** Khai báo các chủ đề hỗ trợ + cờ fastFboSupported=false (Fast FBO không hỗ trợ). */
    Map<String, Object> topics();

    IeJobResponseDto exportProducts(IeProductExportRequestDto request, Long userId);

    IeJobResponseDto importProducts(MultipartFile file, String referenceType, String referenceId, Long userId);

    PageResponse<IeJobResponseDto> listJobs(String jobType, String status, int page, int take);

    /** Như trên, thêm lọc theo tham chiếu (referenceType/referenceId, so khớp không phân biệt hoa thường). */
    PageResponse<IeJobResponseDto> listJobs(String jobType, String status, String referenceType, String referenceId, int page, int take);

    IeJobResponseDto getJob(Long jobId);

    /** Nội dung CSV của job qua link ký (exp, sig). Sai/hết hạn chữ ký -> 403 FILE_LINK_INVALID; file mất -> 404 FILE_NOT_AVAILABLE. */
    IeFileDto downloadFile(Long jobId, Long exp, String sig);

    PageResponse<IeResultRowDto> resultRows(Long jobId, String status, int page, int take);
}
