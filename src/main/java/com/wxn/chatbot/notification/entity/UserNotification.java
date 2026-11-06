package com.wxn.chatbot.notification.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_notifications")
public class UserNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private String type;
    private String title;
    private String resourceType;
    private String resourceId;
    private Instant createdAt;
    private Instant readAt;

    protected UserNotification() {}

    public UserNotification(Long user, String type, String title, String resourceType, String resourceId) {
        this(user, type, title, resourceType, resourceId, Instant.now());
    }

    public UserNotification(
        Long user,
        String type,
        String title,
        String resourceType,
        String resourceId,
        Instant createdAt
    ) {
        this.userId = user;
        this.type = type;
        this.title = title;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.createdAt = createdAt;
    }

    public void read() {
        if (readAt == null) {
            readAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
