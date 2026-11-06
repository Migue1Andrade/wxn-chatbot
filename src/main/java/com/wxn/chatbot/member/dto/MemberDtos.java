package com.wxn.chatbot.member.dto;

import com.wxn.chatbot.common.ActiveStatus;
import com.wxn.chatbot.department.dto.DepartmentDtos;
import com.wxn.chatbot.member.entity.MemberType;
import jakarta.validation.constraints.*;
import java.util.List;

public final class MemberDtos {

    private MemberDtos() {}

    public record Create(
        @NotBlank @Size(max = 200) String name,
        @NotNull MemberType type,
        Long userId,
        ActiveStatus status,
        @Size(max = 40) String phone,
        List<Long> departmentIds
    ) {}

    public record Response(
        Long id,
        Long userId,
        String name,
        MemberType type,
        ActiveStatus status,
        String phone,
        ActiveStatus accountStatus,
        List<DepartmentDtos.Summary> departments
    ) {}
}
