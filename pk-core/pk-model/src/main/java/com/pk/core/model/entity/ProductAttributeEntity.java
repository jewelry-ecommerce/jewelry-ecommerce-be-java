package com.pk.core.model.entity;

import com.pk.core.model.constant.TableConstant;
import com.pk.core.common.entity.UpdateEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import vn.com.unit.miragesql.miragesql.annotation.Column;
import vn.com.unit.miragesql.miragesql.annotation.PrimaryKey;
import vn.com.unit.miragesql.miragesql.annotation.PrimaryKey.GenerationType;
import vn.com.unit.miragesql.miragesql.annotation.Table;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Thuộc tính sản phẩm cấu hình được (Admin Catalog - mục J spec, bảng product_attributes mới ở
 * V2__admin_extensions.sql). extends UpdateEntity (không phải BaseEntity) vì bảng KHÔNG có cột
 * deleted_id/deleted_date - spec chỉ liệt kê GET/POST/PUT cho attributes, không có DELETE, nên
 * không cần xoá mềm. `options` lưu JSON string thô (mảng chuỗi) cho SELECT/MULTISELECT, NULL với
 * TEXT - đơn giản hoá, không tách bảng con product_attribute_options (đủ dùng cho phạm vi đồ án). */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.PRODUCT_ATTRIBUTES)
public class ProductAttributeEntity extends UpdateEntity {

    public static final String TEXT = "TEXT";
    public static final String SELECT = "SELECT";
    public static final String MULTISELECT = "MULTISELECT";

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "product_attributes_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "code")
    private String code;

    @Column(name = "type")
    private String type;

    /** JSON string thô (VD ["Vàng 18K","Vàng 24K"]) - service chịu trách nhiệm serialize/deserialize,
     * entity chỉ lưu chuỗi (giữ Mirage đơn giản, không cần converter riêng). */
    @Column(name = "options")
    private String options;

    public static final String ACTIVE = "ACTIVE";
    public static final String INACTIVE = "INACTIVE";

    @Column(name = "status")
    private String status = ACTIVE;

    /** TEXT | IMAGE (dạng hiển thị giá trị). */
    @Column(name = "display_type")
    private String displayType = "TEXT";

    public ProductAttributeEntity(String name, String code, String type, String options) {
        this.name = name;
        this.code = code;
        this.type = type;
        this.options = options;
    }

    /** Tách `options` (lưu dạng chuỗi phân cách "||", KHÔNG phải JSON thật - tránh phải thêm thư
     * viện JSON chỉ cho 1 cột đơn giản này) thành danh sách - dùng cho SELECT/MULTISELECT. Rỗng/null
     * trả về danh sách rỗng (TEXT không có options). */
    public List<String> getOptionsList() {
        if (options == null || options.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.asList(options.split("\\|\\|"));
    }

    public void setOptionsList(List<String> values) {
        this.options = (values == null || values.isEmpty()) ? null : String.join("||", values);
    }
}
