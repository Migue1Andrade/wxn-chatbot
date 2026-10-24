package com.wxn.chatbot.member.dto;

import com.wxn.chatbot.common.ActiveStatus;
import com.wxn.chatbot.member.entity.MemberType;
import jakarta.validation.constraints.*;

public final class MemberDtos {

    private MemberDtos() {}

    public record Create(
        @NotBlank @Size(max = 200) String name,
        @NotNull MemberType type,
        Long userId,
        ActiveStatus status
    ) {}

    public record Response(
        Long id,
        Long companyId,
        Long userId,
        String name,
        MemberType type,
        ActiveStatus status
    ) {}
}
