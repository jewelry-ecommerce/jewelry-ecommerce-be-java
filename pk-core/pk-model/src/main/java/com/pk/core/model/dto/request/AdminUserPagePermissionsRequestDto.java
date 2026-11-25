package com.pk.core.model.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class AdminUserPagePermissionsRequestDto {

    @NotNull
    @Valid
    private List<Item> items = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Item {
        @NotNull
        private String pageCode;
        private boolean view;
        private boolean edit;
        private boolean delete;
    }
}
