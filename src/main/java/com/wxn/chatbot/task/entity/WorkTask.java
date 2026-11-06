package com.wxn.chatbot.task.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "work_tasks")
public class WorkTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String type;
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    private String conversationId;
    private Long assignedUserId;
    private Long createdByUserId;
    private Instant dueAt;
    private String status;
    private String priority;
    private boolean overdueNotified;

    @Version
    private Long version;

    protected WorkTask() {}

    public WorkTask(Long creator, String conversation) {
        this.createdByUserId = creator;
        this.conversationId = conversation;
    }

    public void update(
        String type,
        String title,
        String description,
        Long assigned,
        Instant due,
        String status,
        String priority
    ) {
        if (!java.util.Objects.equals(due, dueAt) || !java.util.Objects.equals(assigned, assignedUserId)) {
            overdueNotified = false;
        }
        this.type = type;
        this.title = title.trim();
        this.description = description;
        this.assignedUserId = assigned;
        this.dueAt = due;
        this.status = status;
        this.priority = priority;
    }

    public void notified() {
        overdueNotified = true;
    }

    public Long getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getConversationId() {
        return conversationId;
    }

    public Long getAssignedUserId() {
        return assignedUserId;
    }

    public Long getCreatedByUserId() {
        return createdByUserId;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public String getStatus() {
        return status;
    }

    public String getPriority() {
        return priority;
    }

    public Long getVersion() {
        return version;
    }
}
