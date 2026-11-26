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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TaskService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Recife");

    public record Input(
        @NotBlank @Pattern(regexp = "TASK|FOLLOW_UP|REMINDER") String type,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) String description,
        String conversationId,
        @Positive Long assignedUserId,
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
        return list(actor, page, status, null, null);
    }

    public List<Response> list(
        AccountSessions.Identity actor,
        int page,
        String status,
        String type,
        String period
    ) {
        if (
            page < 0 ||
            page > 10000 ||
            (status != null && !Set.of("OPEN", "COMPLETED", "CANCELLED").contains(status)) ||
            (type != null && !Set.of("TASK", "FOLLOW_UP", "REMINDER").contains(type)) ||
            (period != null && !Set.of("OVERDUE", "TODAY", "UPCOMING").contains(period))
        ) {
            throw ApiException.badRequest("Filtro inválido");
        }
        Instant from = Instant.parse("0001-01-01T00:00:00Z");
        Instant to = Instant.parse("9999-12-31T23:59:59Z");
        if (period != null) {
            LocalDate today = LocalDate.now(BUSINESS_ZONE);
            Instant startTomorrow = today.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
            if ("OVERDUE".equals(period)) {
                to = Instant.now();
                status = "OPEN";
            } else if ("TODAY".equals(period)) {
                from = Instant.now();
                to = startTomorrow;
                status = "OPEN";
            } else {
                from = startTomorrow;
                status = "OPEN";
            }
        }
        return repository
            .visibleByPeriod(
                actor.id(),
                actor.role() == UserRole.ADMIN,
                status == null ? "ANY" : status,
                type == null ? "ANY" : type,
                from,
                to,
                PageRequest.of(page, 30)
            )
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
        Long assignedUserId = input.assignedUserId() == null ? actor.id() : input.assignedUserId();
        if (actor.role() != UserRole.ADMIN && !Objects.equals(assignedUserId, actor.id())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Atendente só gerencia suas atividades");
        }
        users.requireActive(assignedUserId);
        if ("FOLLOW_UP".equals(input.type()) && input.conversationId() == null) {
            throw ApiException.badRequest("Follow-up deve estar vinculado a uma conversa");
        }
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
        Instant previousDueAt = t.getDueAt();
        String previousStatus = t.getStatus();
        t.update(
            input.type(),
            input.title(),
            input.description(),
            assignedUserId,
            input.dueAt(),
            input.status(),
            input.priority()
        );
        repository.saveAndFlush(t);
        if (!Objects.equals(previous, assignedUserId)) {
            notifications.create(
                assignedUserId,
                "TASK_ASSIGNED",
                "Uma atividade foi atribuída a você",
                "TASK",
                t.getId().toString()
            );
        }
        if (
            id != null &&
            ("COMPLETED".equals(input.status()) ||
                !Objects.equals(previousDueAt, input.dueAt()) ||
                !Objects.equals(previousStatus, input.status()) ||
                !Objects.equals(previous, assignedUserId))
        ) {
            notifications.dismissTaskOverdue(t.getId().toString(), previous);
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
            String taskId = task.getId().toString();
            String title = "FOLLOW_UP".equals(task.getType())
                ? "Follow-up vencido: " + task.getTitle()
                : "Uma atividade está vencida";
            notifications.createOnce(
                task.getAssignedUserId(),
                "TASK_OVERDUE",
                title,
                "TASK",
                taskId,
                "task-overdue:" + taskId + ":" + task.getDueAt().toEpochMilli() + ":" + task.getVersion()
            );
        }
    }
}
