package com.pk.core.business.service.impl;

import com.pk.core.business.repository.CategoryRepo;
import com.pk.core.business.repository.ImportExportJobRepo;
import com.pk.core.business.repository.ImportJobRowRepo;
import com.pk.core.business.repository.ProductRepo;
import com.pk.core.business.repository.ProductSkuRepo;
import com.pk.core.model.entity.CategoryEntity;
import com.pk.core.model.entity.ImportExportJobEntity;
import com.pk.core.model.entity.ImportJobRowEntity;
import com.pk.core.model.entity.ProductEntity;
import com.pk.core.model.entity.ProductSkuEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * Mọi truy cập DB của luồng import/export đi qua đây, mỗi hàm 1 giao dịch ngắn. Nhờ vậy ImportExportServiceImpl không chạy
 * trong 1 giao dịch lớn: dòng import lỗi chỉ rollback đúng dòng đó (các lệnh ProductService tự có giao dịch riêng).
 */
@Service
@RequiredArgsConstructor
public class ImportExportJobStore {

    private final ImportExportJobRepo jobs;
    private final ImportJobRowRepo rows;
    private final ProductRepo products;
    private final ProductSkuRepo skus;
    private final CategoryRepo categories;

    @Transactional
    public ImportExportJobEntity start(String type, Long userId, String fileName, String refType, String refId, String params) {
        ImportExportJobEntity j = new ImportExportJobEntity();
        j.setJobType(type);
        j.setStatus(ImportExportJobEntity.PROCESSING);
        j.setFileName(fileName);
        j.setReferenceType(refType);
        j.setReferenceId(refId);
        j.setParams(params);
        j.setStartedAt(new Date());
        j.setCreatedId(userId);
        jobs.create(j);
        return j;
    }

    @Transactional
    public void finish(ImportExportJobEntity j, String status, String error, int total, int ok, int failed, String fileUrl) {
        j.setStatus(status);
        j.setErrorMessage(error == null ? null : (error.length() > 1000 ? error.substring(0, 1000) : error));
        j.setTotalRows(total);
        j.setSuccessRows(ok);
        j.setFailedRows(failed);
        if (fileUrl != null) {
            j.setFileUrl(fileUrl);
        }
        j.setFinishedAt(new Date());
        j.touch();
        jobs.update(j);
    }

    @Transactional
    public void addRow(Long jobId, int lineNo, String status, String message, String data) {
        String msg = message == null ? null : (message.length() > 1000 ? message.substring(0, 1000) : message);
        rows.create(new ImportJobRowEntity(jobId, lineNo, status, msg, data));
    }

    @Transactional(readOnly = true)
    public ProductEntity productByCode(String code) {
        return products.findByCode(code);
    }

    @Transactional(readOnly = true)
    public ProductSkuEntity skuByCode(String code) {
        return skus.findBySkuCode(code);
    }

    @Transactional(readOnly = true)
    public CategoryEntity categoryByCode(String code) {
        return categories.findByCode(code);
    }

    @Transactional(readOnly = true)
    public CategoryEntity categoryById(Long id) {
        return categories.findOne(id);
    }

    @Transactional(readOnly = true)
    public ImportExportJobEntity job(Long id) {
        return jobs.findOne(id);
    }

    @Transactional(readOnly = true)
    public List<ImportExportJobEntity> allJobs() {
        return jobs.findAllNewestFirst();
    }

    @Transactional(readOnly = true)
    public List<ImportJobRowEntity> rowsOf(Long jobId) {
        return rows.findByJobId(jobId);
    }
}
