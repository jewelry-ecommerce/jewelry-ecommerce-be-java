package com.pk.core.business.service;

import java.io.IOException;

/** Lưu file CSV (export/import gốc) ở folder RIÊNG TƯ của kho file chung; không có URL công khai, tải qua link ký có hạn. */
public interface ImportExportFileStore {

    /** @return key tương đối (lưu vào job.fileUrl), vd "private/import-export/2026/10/&lt;uuid&gt;.csv". */
    String saveCsv(byte[] content);

    /** Đọc lại nội dung theo key; null nếu file không còn trên máy chủ. */
    byte[] readCsv(String key) throws IOException;
}
