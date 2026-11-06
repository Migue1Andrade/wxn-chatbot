package com.wxn.chatbot.department.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "departments")
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String color;
    private String description;

    @Enumerated(EnumType.STRING)
    private com.wxn.chatbot.common.ActiveStatus status = com.wxn.chatbot.common.ActiveStatus.ACTIVE;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @Version
    private Long version;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    protected Department() {}

    public Department(String name, String color) {
        update(name, color);
        createdAt = updatedAt;
    }

    public void update(String name, String color) {
        this.name = name.trim();
        this.color = color;
        this.updatedAt = OffsetDateTime.now();
    }

    public void details(String description, com.wxn.chatbot.common.ActiveStatus status) {
        this.description = description == null || description.isBlank() ? null : description.trim();
        if (status != null) {
            this.status = status;
        }
    }

    public String getDescription() {
        return description;
    }

    public com.wxn.chatbot.common.ActiveStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void delete() {
        deletedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getColor() {
        return color;
    }
}
