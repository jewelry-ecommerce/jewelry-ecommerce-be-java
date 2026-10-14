package com.pk.core.model.dto.response;

import com.pk.core.model.entity.ImportJobRowEntity;

/** Kết quả 1 dòng import: lineNo là số dòng trong file CSV (dòng 1 = tiêu đề, dữ liệu bắt đầu từ 2). */
public record IeResultRowDto(Long id, int lineNo, String status, String message, String data) {

    public static IeResultRowDto from(ImportJobRowEntity r) {
        return new IeResultRowDto(r.getId(), r.getLineNo(), r.getStatus(), r.getMessage(), r.getData());
    }
}
