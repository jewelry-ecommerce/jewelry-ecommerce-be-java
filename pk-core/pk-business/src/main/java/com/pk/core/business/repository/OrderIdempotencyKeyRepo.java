package com.pk.core.business.repository;

import com.pk.core.model.entity.OrderIdempotencyKeyEntity;
import org.springframework.data.repository.query.Param;

public interface OrderIdempotencyKeyRepo extends PkRepo<OrderIdempotencyKeyEntity, Long> {

    OrderIdempotencyKeyEntity findByOwnerAndKey(@Param("owner") String owner, @Param("key") String key);
}
