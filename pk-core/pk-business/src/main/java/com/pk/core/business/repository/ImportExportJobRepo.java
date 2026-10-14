package com.pk.core.business.repository;

import com.pk.core.model.entity.ImportExportJobEntity;

import java.util.List;

public interface ImportExportJobRepo extends PkRepo<ImportExportJobEntity, Long> {

    /** Tất cả job, mới nhất trước (lọc/phân trang ở service). */
    List<ImportExportJobEntity> findAllNewestFirst();
}
