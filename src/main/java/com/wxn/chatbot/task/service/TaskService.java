package com.wxn.chatbot.task.service;

import com.wxn.chatbot.auth.service.*;
import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.notification.service.NotificationService;
import com.wxn.chatbot.task.entity.WorkTask;
import com.wxn.chatbot.task.repository.WorkTaskRepository;
import com.wxn.chatbot.user.entity.UserRole;
import com.wxn.chatbot.user.service.UserService;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TaskService {

    public record Input(
        @NotBlank @Pattern(regexp = "TASK|FOLLOW_UP|REMINDER") String type,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) String description,
        String conversationId,
        @NotNull @Positive Long assignedUserId,
        @NotNull Instant dueAt,
        @NotBlank @Pattern(regexp = "OPEN|COMPLETED|CANCELLED") String status,
        @NotBlank @Pattern(regexp = "LOW|NORMAL|HIGH|URGENT") String priority,
        Long expectedVersion
    ) {}

    public record Response(
        Long id,
        String type,
        String title,
        String description,
        String conversationId,
        Long assignedUserId,
        Long createdByUserId,
        Instant dueAt,
        String status,
        String priority,
        Long version
    ) {}

    private final WorkTaskRepository repository;
    private final ConversationAuthorization access;
    private final UserService users;
    private final NotificationService notifications;

    public TaskService(
        WorkTaskRepository repository,
        ConversationAuthorization access,
        UserService users,
        NotificationService notifications
    ) {
        this.repository = repository;
        this.access = access;
        this.users = users;
        this.notifications = notifications;
    }

    private Response response(WorkTask t) {
        return new Response(
            t.getId(),
            t.getType(),
            t.getTitle(),
            t.getDescription(),
            t.getConversationId(),
            t.getAssignedUserId(),
            t.getCreatedByUserId(),
            t.getDueAt(),
            t.getStatus(),
            t.getPriority(),
            t.getVersion()
        );
    }

    public List<Response> list(AccountSessions.Identity actor, int page, String status) {
        if (
            page < 0 ||
            page > 10000 ||
            (status != null && !Set.of("OPEN", "COMPLETED", "CANCELLED").contains(status))
        ) {
            throw ApiException.badRequest("Filtro inválido");
        }
        return repository
            .visible(actor.id(), actor.role() == UserRole.ADMIN, status, PageRequest.of(page, 30))
            .stream()
            .map(this::response)
            .toList();
    }

    public Response get(Long id, AccountSessions.Identity actor) {
        var task = repository
            .findById(id)
            .orElseThrow(() -> ApiException.notFound("Atividade não encontrada"));
        if (actor.role() != UserRole.ADMIN && !Objects.equals(actor.id(), task.getAssignedUserId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Atividade de outro usuário");
        }
        return response(task);
    }

    public Response save(Long id, Input input, AccountSessions.Identity actor) {
        if (actor.role() != UserRole.ADMIN && !Objects.equals(input.assignedUserId(), actor.id())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Atendente só gerencia suas atividades");
        }
        users.requireActive(input.assignedUserId());
        var t =
            id == null
                ? new WorkTask(actor.id(), input.conversationId())
                : repository
                      .findById(id)
                      .orElseThrow(() -> ApiException.notFound("Atividade não encontrada"));
        if (
            id != null && actor.role() != UserRole.ADMIN && !Objects.equals(actor.id(), t.getAssignedUserId())
        ) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Atividade de outro usuário");
        }
        if (
            id != null &&
            (!Objects.equals(input.expectedVersion(), t.getVersion()) ||
                !Objects.equals(t.getConversationId(), input.conversationId()))
        ) {
            throw ApiException.conflict("Atividade alterada ou conversa incompatível");
        }
        if (id == null && input.conversationId() != null) {
            access.require(input.conversationId(), actor);
        }
        Long previous = t.getAssignedUserId();
        t.update(
            input.type(),
            input.title(),
            input.description(),
            input.assignedUserId(),
            input.dueAt(),
            input.status(),
            input.priority()
        );
        repository.saveAndFlush(t);
        if (!Objects.equals(previous, input.assignedUserId())) {
            notifications.create(
                input.assignedUserId(),
                "TASK_ASSIGNED",
                "Uma atividade foi atribuída a você",
                "TASK",
                t.getId().toString()
            );
        }
        return response(t);
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void notifyOverdue() {
        for (var task : repository.findTop100ByStatusAndDueAtBeforeAndOverdueNotifiedFalseOrderByDueAt(
            "OPEN",
            Instant.now()
        )) {
            task.notified();
            notifications.create(
                task.getAssignedUserId(),
                "TASK_OVERDUE",
                "Uma atividade está vencida",
                "TASK",
                task.getId().toString()
            );
        }
    }
}
