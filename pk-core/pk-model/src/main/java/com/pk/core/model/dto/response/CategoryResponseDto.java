package com.pk.core.model.dto.response;

import com.pk.core.common.dto.BaseDto;
import com.pk.core.model.entity.CategoryEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class CategoryResponseDto extends BaseDto {

    private Long id;
    private Long parentId;
    private String name;
    private String slug;
    private String description;
    private String imageUrl;
    private int sortOrder;

    /** Mã riêng (khác slug); danh mục cũ chưa có thì = slug. */
    private String code;
    /** ACTIVE | INACTIVE - INACTIVE (hoặc có tổ tiên INACTIVE) không xuất hiện ở storefront. */
    private String status;
    /** Cấp trong cây: 1 = gốc, tối đa 3. */
    private int level;
    private List<String> ancestorCategoryIds = new ArrayList<>();
    private List<String> ancestorCategorySlugs = new ArrayList<>();
    /** Thuộc tính gán trực tiếp / kế thừa từ tổ tiên (readonly) / hiệu lực = kế thừa + trực tiếp. */
    private List<Long> directAttributeIds = new ArrayList<>();
    private List<Long> inheritedAttributeIds = new ArrayList<>();
    private List<Long> effectiveAttributeIds = new ArrayList<>();
    private List<CategoryAttributeRefDto> directAttributes = new ArrayList<>();
    private List<CategoryAttributeRefDto> inheritedAttributes = new ArrayList<>();
    private List<CategoryAttributeRefDto> effectiveAttributes = new ArrayList<>();
    /** Tương thích FE cũ (ApiCategory): id thuộc tính hiệu lực dạng chuỗi + danh sách thuộc tính hiệu lực. */
    private List<String> allowedAttributeIds = new ArrayList<>();
    private List<CategoryAttributeRefDto> allowedFilterAttributes = new ArrayList<>();

    public CategoryResponseDto(Long id, Long parentId, String name, String slug, String description, String imageUrl,
                               int sortOrder) {
        this.id = id;
        this.parentId = parentId;
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.imageUrl = imageUrl;
        this.sortOrder = sortOrder;
        this.code = slug;
        this.status = "ACTIVE";
        this.level = 1;
    }

    public static CategoryResponseDto from(CategoryEntity c) {
        CategoryResponseDto dto = BaseDto.of(new CategoryResponseDto(c.getId(), c.getParentId(), c.getName(),
                c.getSlug(), c.getDescription(), c.getImageUrl(), c.getSortOrder()), c);
        dto.setCode(c.getCode() == null || c.getCode().isBlank() ? c.getSlug() : c.getCode());
        dto.setStatus(c.isActive() ? "ACTIVE" : "INACTIVE");
        return dto;
    }

    // ---- FE ApiCategory ----
    public String getImage() {
        return imageUrl;
    }
}
