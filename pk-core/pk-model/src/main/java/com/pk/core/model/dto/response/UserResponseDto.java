package com.pk.core.model.dto.response;

import com.pk.core.model.entity.RoleEntity;
import com.pk.core.model.entity.UserEntity;

import java.util.List;
import com.pk.core.common.dto.BaseDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDto extends BaseDto {

    private Long id;
    private String email;
    private String fullName;
    private String phone;
    private List<String> roles;
    /** true khi user có role ROOT — bypass mọi page grant. */
    private boolean root;
    /** Grant trang (rỗng nếu ROOT). */
    private List<PagePermissionItemDto> pagePermissions;

    public static UserResponseDto from(UserEntity u) {
        return from(u, List.of());
    }

    public static UserResponseDto from(UserEntity u, List<PagePermissionItemDto> pagePermissions) {
        List<String> roleNames = u.getRoles().stream().map(RoleEntity::getName).sorted().toList();
        boolean root = roleNames.contains(RoleEntity.ROOT);
        return BaseDto.of(new UserResponseDto(u.getId(), u.getEmail(), u.getFullName(), u.getPhone(),
                roleNames, root, root ? List.of() : pagePermissions), u);
    }

    // ---- FE AuthUser: field tính toán, giữ nguyên field cũ ----

    public String getFirstName() {
        return firstNameOf(fullName);
    }

    public String getLastName() {
        return lastNameOf(fullName);
    }

    public String getStatus() {
        return "ACTIVE";
    }

    public String getType() {
        return "CUSTOMER";
    }

    public String getCreatedAt() {
        return getCreatedDate() == null ? null : getCreatedDate().toInstant().toString();
    }

    public String getUpdatedAt() {
        return getUpdatedDate() == null ? null : getUpdatedDate().toInstant().toString();
    }

    /** Họ + đệm của tên đầy đủ (FE hiển thị "lastName firstName"); tên đơn thì để rỗng. */
    private static String lastNameOf(String full) {
        if (full == null) {
            return "";
        }
        String t = full.trim();
        int i = t.lastIndexOf(' ');
        return i < 0 ? "" : t.substring(0, i).trim();
    }

    /** Tên (từ cuối) của tên đầy đủ. */
    private static String firstNameOf(String full) {
        if (full == null) {
            return "";
        }
        String t = full.trim();
        return t.substring(t.lastIndexOf(' ') + 1);
    }
}
