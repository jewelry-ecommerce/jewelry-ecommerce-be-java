package com.pk.core.test.business;

import com.pk.core.business.repository.PreOrderConfigRepo;
import com.pk.core.business.repository.RoleRepo;
import com.pk.core.business.repository.UserRepo;
import com.pk.core.business.service.impl.AdminAccessServiceImpl;
import com.pk.core.business.service.impl.PreOrderServiceImpl;
import com.pk.core.model.dto.request.PreOrderConfigRequestDto;
import com.pk.core.model.dto.response.AdminUserResponseDto;
import com.pk.core.model.dto.response.PreOrderConfigResponseDto;
import com.pk.core.model.dto.response.RoleResponseDto;
import com.pk.core.model.entity.PreOrderConfigEntity;
import com.pk.core.model.entity.RoleEntity;
import com.pk.core.model.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreOrderAndAccessServiceTest {

    @Mock PreOrderConfigRepo configs;
    @Mock com.pk.core.business.repository.ProductRepo products;
    @Mock com.pk.core.business.repository.CollectionRepo collections;
    @Mock UserRepo users;
    @Mock RoleRepo roles;

    private static PreOrderConfigEntity config(long id, boolean enabled, String message) {
        PreOrderConfigEntity c = new PreOrderConfigEntity(enabled, message, 1L);
        c.setId(id);
        return c;
    }

    private static UserEntity user(long id, String phone) {
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setPhone(phone);
        u.setFullName("User " + id);
        u.setPasswordHash("SECRET-HASH");
        return u;
    }

    private static RoleEntity role(long id, String name) {
        RoleEntity r = new RoleEntity(name);
        r.setId(id);
        return r;
    }

    // ------------------------------------------------------------------ pre-order

    @Test
    void createStoresEnabledTrimmedMessageAndAdminId() {
        PreOrderServiceImpl service = new PreOrderServiceImpl(configs, products, collections);

        PreOrderConfigResponseDto dto = service.create(9L, new PreOrderConfigRequestDto(true, "  Đặt trước giao sau 7 ngày "));

        ArgumentCaptor<PreOrderConfigEntity> saved = ArgumentCaptor.forClass(PreOrderConfigEntity.class);
        verify(configs).create(saved.capture());
        assertTrue(saved.getValue().isEnabled());
        assertEquals("Đặt trước giao sau 7 ngày", saved.getValue().getMessage());
        assertEquals(9L, saved.getValue().getCreatedId());
        assertTrue(dto.isEnabled());
        assertEquals("Đặt trước giao sau 7 ngày", dto.getMessage());
    }

    @Test
    void createWithBlankMessageStoresNull() {
        PreOrderServiceImpl service = new PreOrderServiceImpl(configs, products, collections);

        PreOrderConfigResponseDto dto = service.create(9L, new PreOrderConfigRequestDto(false, "   "));

        assertFalse(dto.isEnabled());
        assertNull(dto.getMessage());
    }

    @Test
    void listReturnsNewestFirst() {
        when(configs.findAll(any(Sort.class))).thenReturn(List.of(config(1L, true, "a"), config(3L, false, "c"), config(2L, true, "b")));
        PreOrderServiceImpl service = new PreOrderServiceImpl(configs, products, collections);

        List<PreOrderConfigResponseDto> list = service.list();

        assertEquals(3, list.size());
        assertEquals("3", list.get(0).getId());
        assertEquals("2", list.get(1).getId());
        assertEquals("1", list.get(2).getId());
    }

    // ------------------------------------------------------------------ users / roles

    @Test
    void listUsersSkipsSoftDeletedAttachesSortedRolesAndNeverExposesPasswordHash() throws Exception {
        UserEntity deleted = user(3L, "0900000003");
        deleted.setDeletedDate(new Date());
        when(users.findAll(any(Sort.class))).thenReturn(List.of(user(2L, "0900000002"), deleted, user(1L, "0900000001")));
        when(roles.findByUserId(1L)).thenReturn(List.of(role(2L, "CUSTOMER"), role(1L, "ADMIN")));
        when(roles.findByUserId(2L)).thenReturn(List.of(role(2L, "CUSTOMER")));
        AdminAccessServiceImpl service = new AdminAccessServiceImpl(users, roles);

        List<AdminUserResponseDto> list = service.listUsers();

        assertEquals(2, list.size());
        assertEquals(1L, list.get(0).getId());
        assertEquals(List.of("ADMIN", "CUSTOMER"), list.get(0).getRoles());
        assertEquals(List.of("CUSTOMER"), list.get(1).getRoles());
        // DTO không có trường passwordHash nào.
        for (java.lang.reflect.Field f : AdminUserResponseDto.class.getDeclaredFields()) {
            assertFalse(f.getName().toLowerCase().contains("password"));
        }
    }

    @Test
    void listRolesSortedById() {
        when(roles.findAll(any(Sort.class))).thenReturn(List.of(role(2L, "CUSTOMER"), role(1L, "ADMIN")));
        AdminAccessServiceImpl service = new AdminAccessServiceImpl(users, roles);

        List<RoleResponseDto> list = service.listRoles();

        assertEquals("ADMIN", list.get(0).getName());
        assertEquals("CUSTOMER", list.get(1).getName());
    }
}
