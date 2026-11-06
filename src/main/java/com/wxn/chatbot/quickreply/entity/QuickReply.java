package com.wxn.chatbot.quickreply.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "quick_replies")
public class QuickReply {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String shortcut;
    private String description;

    @Column(columnDefinition = "text")
    private String content;

    private String scope;
    private Long ownerUserId;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;

    @Version
    private Long version;

    protected QuickReply() {}

    public QuickReply(String scope, Long owner) {
        this.scope = scope;
        this.ownerUserId = owner;
        this.createdAt = Instant.now();
    }

    public void update(String name, String shortcut, String description, String content, boolean active) {
        this.name = name.trim();
        this.shortcut = shortcut.trim().toLowerCase(java.util.Locale.ROOT);
        this.description = description;
        this.content = content;
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public void delete() {
        deletedAt = Instant.now();
        active = false;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getShortcut() {
        return shortcut;
    }

    public String getDescription() {
        return description;
    }

    public String getContent() {
        return content;
    }

    public String getScope() {
        return scope;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Long getVersion() {
        return version;
    }
}
