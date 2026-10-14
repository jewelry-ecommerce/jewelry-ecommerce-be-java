package com.pk.core.model.dto.response;

import com.pk.core.model.entity.ImportExportJobEntity;

import java.util.Date;

/** Trạng thái 1 job import/export. `jobId` giữ cùng giá trị `id` để khớp hợp đồng FE (ie/job/status/{jobId}). */
public record IeJobResponseDto(Long jobId, Long id, String jobType, String entityType, String status, String fileName,
                               String fileUrl, String errorMessage, int totalRows, int successRows, int failedRows,
                               String referenceType, String referenceId, String startedAt, String finishedAt,
                               String createdAt) {

    public static IeJobResponseDto from(ImportExportJobEntity j) {
        return new IeJobResponseDto(j.getId(), j.getId(), j.getJobType(), j.getEntityType(), j.getStatus(),
                j.getFileName(), j.getFileUrl(), j.getErrorMessage(), j.getTotalRows(), j.getSuccessRows(),
                j.getFailedRows(), j.getReferenceType(), j.getReferenceId(), iso(j.getStartedAt()),
                iso(j.getFinishedAt()), iso(j.getCreatedDate()));
    }

    private static String iso(Date d) {
        return d == null ? null : java.time.Instant.ofEpochMilli(d.getTime()).toString();
    }
}
