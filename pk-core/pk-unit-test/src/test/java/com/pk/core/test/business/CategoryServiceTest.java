package com.pk.core.test.business;

import com.pk.core.business.repository.CategoryAttributeRepo;
import com.pk.core.business.repository.CategoryRepo;
import com.pk.core.business.repository.ProductAttributeRepo;
import com.pk.core.business.repository.ProductRepo;
import com.pk.core.business.service.impl.CategoryServiceImpl;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.model.dto.request.AdminCategoryRequestDto;
import com.pk.core.model.dto.response.CategoryResponseDto;
import com.pk.core.model.entity.CategoryAttributeEntity;
import com.pk.core.model.entity.CategoryEntity;
import com.pk.core.model.entity.ProductAttributeEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** FE v5 (PIM): cây danh mục tối đa 3 cấp, thuộc tính kế thừa, ẩn danh mục INACTIVE, chặn xoá khi còn dùng. */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock CategoryRepo categories;
    @Mock ProductRepo products;
    @Mock CategoryAttributeRepo categoryAttributes;
    @Mock ProductAttributeRepo attributes;

    private CategoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CategoryServiceImpl(categories, products, categoryAttributes, attributes);
    }

    private static CategoryEntity cat(long id, Long parentId, boolean active) {
        CategoryEntity c = new CategoryEntity("Danh mục " + id, "c" + id, null);
        c.setId(id);
        c.setCode("c" + id);
        c.setParentId(parentId);
        c.setActive(active);
        return c;
    }

    private static ProductAttributeEntity attr(long id) {
        ProductAttributeEntity a = new ProductAttributeEntity("Thuộc tính " + id, "attr" + id, "SELECT", null);
        a.setId(id);
        return a;
    }

    /** Dựng cây + thuộc tính gán trực tiếp mà service nạp qua findAll. */
    private void tree(List<CategoryEntity> cats, List<CategoryAttributeEntity> links, List<ProductAttributeEntity> attrs) {
        lenient().when(categories.findAll(Sort.unsorted())).thenReturn(cats);
        lenient().when(categoryAttributes.findAll(Sort.unsorted())).thenReturn(links);
        lenient().when(attributes.findAll(Sort.unsorted())).thenReturn(attrs);
    }

    private static AdminCategoryRequestDto req(String name, Long parentId) {
        return new AdminCategoryRequestDto(name, null, null, null, parentId, 0);
    }

    private static String code(BusinessException ex) {
        return ex.getCode();
    }

    @Test
    void rejectsParentThatWouldMakeLevelFour() {
        tree(List.of(cat(1, null, true), cat(2, 1L, true), cat(3, 2L, true)), List.of(), List.of());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req("Cấp bốn", 3L)));

        assertEquals("CATEGORY_PARENT_INVALID", code(ex));
        verify(categories, never()).create(any());
    }

    @Test
    void allowsLevelThree() {
        tree(List.of(cat(1, null, true), cat(2, 1L, true)), List.of(), List.of());

        service.create(req("Cấp ba", 2L));

        verify(categories).create(any(CategoryEntity.class));
    }

    @Test
    void rejectsMovingCategoryUnderItsOwnSubtree() {
        CategoryEntity root = cat(1, null, true);
        tree(List.of(root, cat(2, 1L, true)), List.of(), List.of());
        when(categories.findOne(1L)).thenReturn(root);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(1L, req("Gốc", 2L)));

        assertEquals("CATEGORY_PARENT_INVALID", code(ex));
        verify(categories, never()).update(any());
    }

    @Test
    void rejectsSelfAsParent() {
        CategoryEntity c = cat(1, null, true);
        tree(List.of(c), List.of(), List.of());
        when(categories.findOne(1L)).thenReturn(c);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(1L, req("Tự cha", 1L)));

        assertEquals("CATEGORY_PARENT_INVALID", code(ex));
    }

    @Test
    void rejectsMoveWhenSubtreeDepthWouldOverflow() {
        // 6 > 5 (cấp 2); 1 > 2 (cây con cao 1). Chuyển 1 vào dưới 5 => 4 cấp.
        CategoryEntity moving = cat(1, null, true);
        tree(List.of(cat(6, null, true), cat(5, 6L, true), moving, cat(2, 1L, true)), List.of(), List.of());
        when(categories.findOne(1L)).thenReturn(moving);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(1L, req("Chuyển", 5L)));

        assertEquals("CATEGORY_PARENT_INVALID", code(ex));
    }

    @Test
    void rejectsUnknownParent() {
        tree(List.of(cat(1, null, true)), List.of(), List.of());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req("Mồ côi", 99L)));

        assertEquals("CATEGORY_PARENT_INVALID", code(ex));
    }

    @Test
    void rejectsAttributeAlreadyInheritedFromParent() {
        tree(List.of(cat(1, null, true)), List.of(new CategoryAttributeEntity(1L, 100L)), List.of(attr(100)));
        AdminCategoryRequestDto request = req("Con", 1L);
        request.setDirectAttributeIds(List.of(100L));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(request));

        assertEquals("CATEGORY_ATTRIBUTE_INVALID", code(ex));
        verify(categories, never()).create(any());
    }

    @Test
    void rejectsUnknownAttribute() {
        tree(List.of(), List.of(), List.of(attr(100)));
        AdminCategoryRequestDto request = req("Gốc", null);
        request.setDirectAttributeIds(List.of(555L));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(request));

        assertEquals("CATEGORY_ATTRIBUTE_INVALID", code(ex));
    }

    @Test
    void duplicateCodeIsConflict() {
        when(categories.findByCode("trung")).thenReturn(cat(9, null, true));
        AdminCategoryRequestDto request = req("Mới", null);
        request.setCode("trung");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(request));

        assertEquals("DUPLICATE_CODE", code(ex));
    }

    @Test
    void computesDirectInheritedAndEffectiveAttributes() {
        tree(List.of(cat(1, null, true), cat(2, 1L, true)),
                List.of(new CategoryAttributeEntity(1L, 100L), new CategoryAttributeEntity(2L, 101L)),
                List.of(attr(100), attr(101)));

        CategoryResponseDto child = service.findAllForAdmin().stream()
                .filter(c -> c.getId() == 2L).findFirst().orElseThrow();

        assertEquals(List.of(101L), child.getDirectAttributeIds());
        assertEquals(List.of(100L), child.getInheritedAttributeIds());
        assertEquals(List.of(100L, 101L), child.getEffectiveAttributeIds());
        assertEquals(List.of("1"), child.getAncestorCategoryIds());
        assertEquals(2, child.getLevel());
    }

    @Test
    void storefrontHidesInactiveCategoryAndItsChildren() {
        tree(List.of(cat(1, null, true), cat(2, null, false), cat(3, 2L, true), cat(4, 1L, true)),
                List.of(), List.of());

        List<Long> ids = service.findAllActive().stream().map(CategoryResponseDto::getId).sorted().toList();

        assertEquals(List.of(1L, 4L), ids);
    }

    @Test
    void updateWithNullAttributeIdsKeepsExistingLinks() {
        CategoryEntity c = cat(1, null, true);
        tree(List.of(c), List.of(new CategoryAttributeEntity(1L, 100L)), List.of(attr(100)));
        when(categories.findOne(1L)).thenReturn(c);

        service.update(1L, req("Đổi tên", null));

        verify(categoryAttributes, never()).deleteByCategoryId(any());
    }

    @Test
    void updateWithEmptyAttributeIdsClearsLinks() {
        CategoryEntity c = cat(1, null, true);
        tree(List.of(c), List.of(new CategoryAttributeEntity(1L, 100L)), List.of(attr(100)));
        when(categories.findOne(1L)).thenReturn(c);
        AdminCategoryRequestDto request = req("Đổi tên", null);
        request.setDirectAttributeIds(new ArrayList<>());

        service.update(1L, request);

        verify(categoryAttributes).deleteByCategoryId(1L);
    }

    @Test
    void deleteBlockedWhenHasChildren() {
        CategoryEntity c = cat(1, null, true);
        when(categories.findOne(1L)).thenReturn(c);
        when(categories.countChildren(1L)).thenReturn(2L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(1L));

        assertEquals("CATEGORY_IN_USE", code(ex));
        verify(categories, never()).delete(any(CategoryEntity.class));
    }

    @Test
    void deleteBlockedWhenHasProducts() {
        CategoryEntity c = cat(1, null, true);
        when(categories.findOne(1L)).thenReturn(c);
        when(categories.countChildren(1L)).thenReturn(0L);
        when(products.countByCategoryId(1L)).thenReturn(3L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(1L));

        assertEquals("CATEGORY_IN_USE", code(ex));
        verify(categories, never()).delete(any(CategoryEntity.class));
    }

    @Test
    void deleteRemovesCategoryAndItsAttributeLinks() {
        CategoryEntity c = cat(1, null, true);
        when(categories.findOne(1L)).thenReturn(c);
        when(categories.countChildren(1L)).thenReturn(0L);
        when(products.countByCategoryId(1L)).thenReturn(0L);

        service.delete(1L);

        verify(categoryAttributes).deleteByCategoryId(1L);
        verify(categories).delete(c);
    }
}
