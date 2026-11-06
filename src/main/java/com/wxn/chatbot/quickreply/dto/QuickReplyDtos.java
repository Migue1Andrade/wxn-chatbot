package com.wxn.chatbot.quickreply.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public final class QuickReplyDtos {

    private QuickReplyDtos() {}

    public record Input(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Pattern(regexp = "[a-zA-Z0-9_]{2,50}") String shortcut,
        @Size(max = 300) String description,
        @NotBlank @Size(max = 1600) String content,
        @NotBlank @Pattern(regexp = "GLOBAL|PERSONAL") String scope,
        boolean active,
        Long expectedVersion
    ) {}

    public record Response(
        Long id,
        String name,
        String shortcut,
        String description,
        String content,
        String scope,
        boolean active,
        Long version
    ) {}

    public record PreviewInput(@NotBlank String conversationId, @NotBlank @Size(max = 1600) String content) {}

    public record Preview(String content, List<String> missingVariables) {}
}
