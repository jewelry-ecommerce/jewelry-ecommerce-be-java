package com.pk.core.model.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** PATCH /reorder: gán lại sortOrder cho các nút (thường là anh em cùng cha). */
@Getter
@Setter
@NoArgsConstructor
public class AdminNavigationReorderRequestDto {

    private List<Item> items;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Item {
        private Long id;
        private Integer sortOrder;
    }
}
