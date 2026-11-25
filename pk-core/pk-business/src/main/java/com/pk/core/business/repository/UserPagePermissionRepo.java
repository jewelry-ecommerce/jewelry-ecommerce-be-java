package com.pk.core.business.repository;

import com.pk.core.model.entity.UserPagePermissionEntity;
import org.springframework.data.repository.query.Param;
import vn.com.unit.springframework.data.mirage.repository.query.Modifying;

import java.util.List;

public interface UserPagePermissionRepo extends PkRepo<UserPagePermissionEntity, Long> {

    List<UserPagePermissionEntity> findByUserId(@Param("userId") Long userId);

    @Modifying
    int deleteByUserId(@Param("userId") Long userId);
}
