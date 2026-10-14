package com.pk.core.test.business;

import com.pk.core.business.repository.CategoryAttributeRepo;
import com.pk.core.business.repository.ProductAttributeRepo;
import com.pk.core.business.repository.ProductAttributeValueRepo;
import com.pk.core.business.repository.SkuAttributeValueRepo;
import com.pk.core.business.service.impl.ProductAttributeServiceImpl;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.model.dto.request.AdminAttributeRequestDto;
import com.pk.core.model.dto.request.AdminAttributeValueRequestDto;
import com.pk.core.model.dto.response.ProductAttributeResponseDto;
import com.pk.core.model.entity.ProductAttributeEntity;
import com.pk.core.model.entity.ProductAttributeValueEntity;
import com.pk.core.model.entity.SkuAttributeValueEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** FE v5 (PIM): giá trị thuộc tính có id/mã/ảnh; đổi tên lan sang SKU; chặn xoá giá trị/thuộc tính đang dùng. */
@ExtendWith(MockitoExtension.class)
class ProductAttributeServiceTest {

    @Mock ProductAttributeRepo attributes;
    @Mock ProductAttributeValueRepo valueRepo;
    @Mock SkuAttributeValueRepo skuValues;
    @Mock CategoryAttributeRepo categoryAttributes;

    private ProductAttributeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProductAttributeServiceImpl(attributes, valueRepo, skuValues, categoryAttributes);
    }

    private static ProductAttributeEntity attribute() {
        ProductAttributeEntity a = new ProductAttributeEntity("Chất liệu", "material", "MULTISELECT", null);
        a.setId(10L);
        return a;
    }

    private static ProductAttributeValueEntity value(long id, String code, String value, int sort) {
        ProductAttributeValueEntity v = new ProductAttributeValueEntity();
        v.setId(id);
        v.setAttributeId(10L);
        v.setCode(code);
        v.setValue(value);
        v.setSortOrder(sort);
        return v;
    }

    private static AdminAttributeValueRequestDto item(Long id, String code, String value) {
        AdminAttributeValueRequestDto v = new AdminAttributeValueRequestDto();
        v.setId(id);
        v.setCode(code);
        v.setValue(value);
        return v;
    }

    private static AdminAttributeRequestDto request(List<AdminAttributeValueRequestDto> values) {
        AdminAttributeRequestDto r = new AdminAttributeRequestDto("Chất liệu", "material", "MULTISELECT", null);
        r.setValues(values);
        return r;
    }

    private void existing(ProductAttributeValueEntity... rows) {
        when(attributes.findOne(10L)).thenReturn(attribute());
        when(valueRepo.findByAttributeId(10L)).thenReturn(new ArrayList<>(List.of(rows)));
    }

    @Test
    void renamingValueCascadesToSkuRowsIncludingMultiselect() {
        existing(value(1, "vang", "Vàng", 0), value(2, "bac", "Bạc", 1));
        SkuAttributeValueEntity sku = new SkuAttributeValueEntity(5L, 10L, "Vàng||Bạc");
        when(skuValues.findContaining(10L, "Vàng")).thenReturn(List.of(sku));

        ProductAttributeResponseDto dto = service.update(10L,
                request(List.of(item(1L, null, "Vàng 18K"), item(2L, null, "Bạc"))));

        assertEquals("Vàng 18K||Bạc", sku.getValue());
        verify(skuValues).update(sku);
        assertEquals(List.of("Vàng 18K", "Bạc"), dto.getOptions());
    }

    @Test
    void removingValueUsedBySkuIsConflict() {
        existing(value(1, "vang", "Vàng", 0), value(2, "bac", "Bạc", 1));
        when(skuValues.countByAttributeIdAndValue(10L, "Bạc")).thenReturn(2L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(10L, request(List.of(item(1L, null, "Vàng")))));

        assertEquals("ATTRIBUTE_VALUE_IN_USE", ex.getCode());
        verify(valueRepo, never()).delete(any(ProductAttributeValueEntity.class));
    }

    @Test
    void removingUnusedValueDeletesIt() {
        ProductAttributeValueEntity bac = value(2, "bac", "Bạc", 1);
        existing(value(1, "vang", "Vàng", 0), bac);
        when(skuValues.countByAttributeIdAndValue(10L, "Bạc")).thenReturn(0L);

        service.update(10L, request(List.of(item(1L, null, "Vàng"))));

        verify(valueRepo).delete(bac);
    }

    @Test
    void storedValueCodeCannotBeChanged() {
        existing(value(1, "vang", "Vàng", 0));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(10L, request(List.of(item(1L, "gold", "Vàng")))));

        assertEquals("ATTRIBUTE_VALUE_INVALID", ex.getCode());
        verify(valueRepo, never()).update(any());
    }

    @Test
    void unknownValueIdIsRejected() {
        existing(value(1, "vang", "Vàng", 0));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(10L, request(List.of(item(77L, null, "Lạ")))));

        assertEquals("ATTRIBUTE_VALUE_INVALID", ex.getCode());
    }

    @Test
    void duplicateValueNamesAreRejected() {
        existing(value(1, "vang", "Vàng", 0));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(10L, request(List.of(item(null, null, "Vàng"), item(null, "x", "Vàng")))));

        assertEquals("ATTRIBUTE_VALUE_INVALID", ex.getCode());
    }

    @Test
    void legacyOptionsKeepExistingIdsAndAppendNewValues() {
        ProductAttributeValueEntity vang = value(1, "Vàng", "Vàng", 0);
        ProductAttributeValueEntity bac = value(2, "Bạc", "Bạc", 1);
        existing(vang, bac);
        AdminAttributeRequestDto legacy = new AdminAttributeRequestDto("Chất liệu", "material", "MULTISELECT",
                List.of("Bạc", "Vàng", "Platinum"));

        ProductAttributeResponseDto dto = service.update(10L, legacy);

        assertEquals(1L, vang.getId());
        assertEquals(1, vang.getSortOrder());
        assertEquals(0, bac.getSortOrder());
        ArgumentCaptor<ProductAttributeValueEntity> created = ArgumentCaptor.forClass(ProductAttributeValueEntity.class);
        verify(valueRepo, times(1)).create(created.capture());
        assertEquals("Platinum", created.getValue().getCode());
        assertEquals(List.of("Bạc", "Vàng", "Platinum"), dto.getOptions());
    }

    @Test
    void updateWithoutValuesKeepsExistingValues() {
        existing(value(1, "vang", "Vàng", 0));
        AdminAttributeRequestDto onlyName = new AdminAttributeRequestDto("Chất liệu mới", "material", "MULTISELECT", null);

        ProductAttributeResponseDto dto = service.update(10L, onlyName);

        verify(valueRepo, never()).create(any());
        verify(valueRepo, never()).delete(any(ProductAttributeValueEntity.class));
        assertEquals(1, dto.getValues().size());
    }

    @Test
    void createFromLegacyOptionsUsesNameAsCode() {
        AdminAttributeRequestDto legacy = new AdminAttributeRequestDto("Chất liệu", "material", "SELECT",
                List.of("Vàng", "Bạc"));

        service.create(legacy);

        ArgumentCaptor<ProductAttributeValueEntity> created = ArgumentCaptor.forClass(ProductAttributeValueEntity.class);
        verify(valueRepo, times(2)).create(created.capture());
        assertEquals("Vàng", created.getAllValues().get(0).getCode());
        assertEquals("Bạc", created.getAllValues().get(1).getCode());
        assertEquals(1, created.getAllValues().get(1).getSortOrder());
    }

    @Test
    void createRejectsDuplicateCode() {
        when(attributes.findByCode("material")).thenReturn(attribute());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(new AdminAttributeRequestDto("Chất liệu", "material", "SELECT", null)));

        assertEquals("DUPLICATE_CODE", ex.getCode());
    }

    @Test
    void rejectsInvalidType() {
        when(attributes.findByCode("x")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(new AdminAttributeRequestDto("X", "x", "RADIO", null)));

        assertEquals("VALIDATION_FAILED", ex.getCode());
    }

    @Test
    void deleteBlockedWhenSkusUseAttribute() {
        when(attributes.findOne(10L)).thenReturn(attribute());
        when(skuValues.countByAttributeId(10L)).thenReturn(4L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(10L));

        assertEquals("ATTRIBUTE_IN_USE", ex.getCode());
        verify(attributes, never()).delete(any(ProductAttributeEntity.class));
    }

    @Test
    void deleteBlockedWhenCategoriesUseAttribute() {
        when(attributes.findOne(10L)).thenReturn(attribute());
        when(skuValues.countByAttributeId(10L)).thenReturn(0L);
        when(categoryAttributes.countByAttributeId(10L)).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(10L));

        assertEquals("ATTRIBUTE_IN_USE", ex.getCode());
    }

    @Test
    void deleteUnusedAttribute() {
        ProductAttributeEntity a = attribute();
        when(attributes.findOne(10L)).thenReturn(a);
        when(skuValues.countByAttributeId(10L)).thenReturn(0L);
        when(categoryAttributes.countByAttributeId(10L)).thenReturn(0L);

        service.delete(10L);

        verify(attributes).delete(a);
    }
}
