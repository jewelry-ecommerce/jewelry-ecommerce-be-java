package com.pk.core.model.entity;

import com.pk.core.model.constant.TableConstant;
import com.pk.core.common.entity.CreateEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import vn.com.unit.miragesql.miragesql.annotation.Column;
import vn.com.unit.miragesql.miragesql.annotation.PrimaryKey;
import vn.com.unit.miragesql.miragesql.annotation.PrimaryKey.GenerationType;
import vn.com.unit.miragesql.miragesql.annotation.Table;

@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.ROLES)
public class RoleEntity extends CreateEntity {

    public static final String ADMIN = "ADMIN";
    public static final String CUSTOMER = "CUSTOMER";
    /** Superuser phân quyền trang — chỉ ROOT thấy / quản lý màn phân quyền. */
    public static final String ROOT = "ROOT";
    /** Nhân viên quản lý danh mục/nội dung (sản phẩm, khuyến mãi, CMS, banner, badge). */
    public static final String CATALOG_MANAGER = "CATALOG_MANAGER";
    /** Nhân viên quản lý đơn hàng/kho/khách hàng/yêu cầu khách. */
    public static final String ORDER_MANAGER = "ORDER_MANAGER";
    /** Các vai trò nhân viên được vào khu quản trị (ROOT/ADMIN toàn quyền API thô). */
    public static final java.util.Set<String> STAFF_ROLES = java.util.Set.of(ROOT, ADMIN, CATALOG_MANAGER, ORDER_MANAGER);
    /** Các vai trò mà admin được gán cho tài khoản (ROOT không gán qua API roles). */
    public static final java.util.Set<String> ASSIGNABLE_ROLES = java.util.Set.of(ADMIN, CATALOG_MANAGER, ORDER_MANAGER,
            CUSTOMER);

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "roles_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "name")
    private String name;

    public RoleEntity(String name) {
        this.name = name;
    }
}
