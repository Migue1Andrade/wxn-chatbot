package com.wxn.chatbot.user.dto;

import com.wxn.chatbot.common.ActiveStatus;
import com.wxn.chatbot.department.dto.DepartmentDtos;
import com.wxn.chatbot.user.entity.UserRole;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;

public final class UserDtos {

    private UserDtos() {}

    public record Create(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Size(max = 254) String email,
        @NotBlank String cpf,
        @Size(max = 40) String phone,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank String passwordConfirmation,
        @NotNull UserRole role,
        @NotNull ActiveStatus accountStatus,
        List<@NotNull Long> departmentIds
    ) {}

    public record Update(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Size(max = 254) String email,
        @NotBlank String cpf,
        @Size(max = 40) String phone,
        @NotNull UserRole role,
        @NotNull ActiveStatus accountStatus,
        List<@NotNull Long> departmentIds,
        @Size(min = 8, max = 72) String initialPassword,
        String passwordConfirmation
    ) {}

    public record Response(
        Long id,
        String name,
        String username,
        String email,
        String cpf,
        String phone,
        UserRole role,
        ActiveStatus accountStatus,
        Long memberId,
        List<DepartmentDtos.Summary> departments,
        boolean registrationIncomplete,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
    ) {}
}
