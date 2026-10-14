package com.pk.core.business.repository;

import com.pk.core.model.entity.ImportJobRowEntity;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ImportJobRowRepo extends PkRepo<ImportJobRowEntity, Long> {

    List<ImportJobRowEntity> findByJobId(@Param("jobId") Long jobId);
}
