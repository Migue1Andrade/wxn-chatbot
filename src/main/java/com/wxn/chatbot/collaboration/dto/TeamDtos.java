package com.wxn.chatbot.collaboration.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class TeamDtos {

    private TeamDtos() {}

    public record Create(
        @NotBlank @Size(max = 5000) String content,
        @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{16,100}") String operationId,
        @Pattern(regexp = "SAGE|SKY|LAVENDER|PEACH|SUNSHINE|ROSE") String color
    ) {}

    public record Edit(
        @NotBlank @Size(max = 5000) String content,
        boolean pinned,
        @Min(0) long expectedVersion,
        @Pattern(regexp = "SAGE|SKY|LAVENDER|PEACH|SUNSHINE|ROSE") String color
    ) {}

    public record Entry(
        String id,
        String conversationId,
        Long authorUserId,
        String authorName,
        String kind,
        String content,
        boolean pinned,
        Instant createdAt,
        Instant updatedAt,
        long version,
        String color
    ) {}

    public record Revision(String content, Long actorUserId, Instant at) {}

    public record Draft(
        @NotBlank @Pattern(regexp = "EXTERNAL|INTERNAL") String mode,
        @Size(max = 5000) String content
    ) {}
}
