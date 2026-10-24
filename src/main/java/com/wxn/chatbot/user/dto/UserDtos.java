package com.wxn.chatbot.user.dto;

import jakarta.validation.constraints.*;

public final class UserDtos {

    private UserDtos() {}

    public record Create(@NotBlank @Size(max = 200) String name, @Size(max = 40) String phone) {}

    public record Response(Long id, Long companyId, String name, String phone) {}
}
