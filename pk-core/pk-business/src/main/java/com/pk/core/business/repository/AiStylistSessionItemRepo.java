package com.pk.core.business.repository;

import com.pk.core.model.entity.AiStylistSessionItemEntity;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AiStylistSessionItemRepo extends PkRepo<AiStylistSessionItemEntity, Long> {

    List<AiStylistSessionItemEntity> findBySessionId(@Param("sessionId") Long sessionId);
}
