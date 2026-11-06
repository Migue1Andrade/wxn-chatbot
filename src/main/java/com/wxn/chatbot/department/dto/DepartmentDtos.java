package com.wxn.chatbot.department.dto;

import jakarta.validation.constraints.*;

public final class DepartmentDtos {

    private DepartmentDtos() {}

    public record Save(
        @NotBlank @Size(max = 200) String name,
        @NotNull @Pattern(regexp = "#[0-9a-fA-F]{6}") String color,
        @Size(max = 500) String description,
        com.wxn.chatbot.common.ActiveStatus status,
        @PositiveOrZero Long expectedVersion
    ) {
        public Save(String name, String color) {
            this(name, color, null, com.wxn.chatbot.common.ActiveStatus.ACTIVE, null);
        }
    }

    public record Summary(Long id, String name, String color) {}

    public record Response(
        Long id,
        String name,
        String color,
        long memberCount,
        String description,
        com.wxn.chatbot.common.ActiveStatus status,
        java.time.OffsetDateTime createdAt,
        java.time.OffsetDateTime updatedAt,
        Long version
    ) {}

    public record Member(Long id, String name, String type, String status) {}
}
