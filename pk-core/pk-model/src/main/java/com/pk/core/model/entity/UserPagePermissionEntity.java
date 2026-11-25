package com.pk.core.model.entity;

import com.pk.core.common.entity.UpdateEntity;
import com.pk.core.model.constant.TableConstant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import vn.com.unit.miragesql.miragesql.annotation.Column;
import vn.com.unit.miragesql.miragesql.annotation.PrimaryKey;
import vn.com.unit.miragesql.miragesql.annotation.PrimaryKey.GenerationType;
import vn.com.unit.miragesql.miragesql.annotation.Table;

/** Quyền trang admin theo user: xem / sửa / xóa (V43). */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.USER_PAGE_PERMISSIONS)
public class UserPagePermissionEntity extends UpdateEntity {

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "user_page_permissions_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "page_code")
    private String pageCode;

    @Column(name = "can_view")
    private boolean canView;

    @Column(name = "can_edit")
    private boolean canEdit;

    @Column(name = "can_delete")
    private boolean canDelete;

    public UserPagePermissionEntity(Long userId, String pageCode, boolean canView, boolean canEdit, boolean canDelete) {
        this.userId = userId;
        this.pageCode = pageCode;
        this.canView = canView;
        this.canEdit = canEdit;
        this.canDelete = canDelete;
    }
}
