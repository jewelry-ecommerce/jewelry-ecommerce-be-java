package com.pk.core.model.entity;

import com.pk.core.common.entity.CreateEntity;
import com.pk.core.model.constant.TableConstant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import vn.com.unit.miragesql.miragesql.annotation.Column;
import vn.com.unit.miragesql.miragesql.annotation.PrimaryKey;
import vn.com.unit.miragesql.miragesql.annotation.PrimaryKey.GenerationType;
import vn.com.unit.miragesql.miragesql.annotation.Table;

/** Thuộc tính gán TRỰC TIẾP cho danh mục; danh mục con kế thừa thêm từ cha ở tầng service. */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.CATEGORY_ATTRIBUTES)
public class CategoryAttributeEntity extends CreateEntity {

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "category_attributes_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "attribute_id")
    private Long attributeId;

    public CategoryAttributeEntity(Long categoryId, Long attributeId) {
        this.categoryId = categoryId;
        this.attributeId = attributeId;
    }
}
