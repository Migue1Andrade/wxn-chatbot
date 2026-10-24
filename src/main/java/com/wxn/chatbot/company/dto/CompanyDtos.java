package com.wxn.chatbot.company.dto;

import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;

public final class CompanyDtos {

    private CompanyDtos() {}

    public record Create(@NotBlank @Size(max = 200) String name) {}

    public record Response(Long id, String name, OffsetDateTime createdAt) {}
}
