package com.wxn.chatbot.notification.service;

import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.notification.entity.UserNotification;
import com.wxn.chatbot.notification.repository.NotificationRepository;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;

@Service
@Transactional
public class NotificationService {

    public record Response(
        Long id,
        String type,
        String title,
        String resourceType,
        String resourceId,
        Instant createdAt,
        Instant readAt
    ) {}

    private final NotificationRepository repository;
    private final org.springframework.context.ApplicationEventPublisher events;

    public record Created(Long userId, Long notificationId) {}

    public NotificationService(
        NotificationRepository repository,
        org.springframework.context.ApplicationEventPublisher events
    ) {
        this.repository = repository;
        this.events = events;
    }

    private Response response(UserNotification n) {
        return new Response(
            n.getId(),
            n.getType(),
            n.getTitle(),
            n.getResourceType(),
            n.getResourceId(),
            n.getCreatedAt(),
            n.getReadAt()
        );
    }

    public void create(Long user, String type, String title, String resourceType, String resourceId) {
        var n = repository.saveAndFlush(new UserNotification(user, type, title, resourceType, resourceId));
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                public void afterCommit() {
                    events.publishEvent(new Created(user, n.getId()));
                }
            }
        );
    }

    public void createOnce(Long user, String type, String title, String resource, String id, String key) {
        if (repository.insertOnce(user, type, title, resource, id, key) == 1) {
            var notification = repository.byDeliveryKey(key).orElseThrow();
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    public void afterCommit() {
                        events.publishEvent(new Created(user, notification.getId()));
                    }
                }
            );
        }
    }

    public List<Response> list(Long user, int page) {
        if (page < 0 || page > 10000) {
            throw ApiException.badRequest("Página inválida");
        }
        return repository
            .findByUserIdOrderByCreatedAtDescIdDesc(user, PageRequest.of(page, 30))
            .stream()
            .map(this::response)
            .toList();
    }

    public long count(Long user) {
        return repository.countByUserIdAndReadAtIsNull(user);
    }

    public void read(Long user, Long id) {
        repository
            .findById(id)
            .filter(n -> Objects.equals(n.getUserId(), user))
            .orElseThrow(() -> ApiException.notFound("Notificação não encontrada"))
            .read();
    }

    public void readAll(Long user) {
        repository.readAll(user);
    }
}
