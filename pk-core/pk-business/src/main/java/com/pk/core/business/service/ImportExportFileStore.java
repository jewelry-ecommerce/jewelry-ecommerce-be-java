package com.pk.core.business.service;

/** Lưu file CSV (export/import gốc) cạnh thư mục upload; trả URL công khai (tên UUID khó đoán). */
public interface ImportExportFileStore {

    /** @return URL tải file. */
    String saveCsv(byte[] content);
}
