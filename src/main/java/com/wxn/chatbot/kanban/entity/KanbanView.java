package com.wxn.chatbot.kanban.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "kanban_views")
public class KanbanView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long ownerUserId;
    private String name;

    @Column(columnDefinition = "text")
    private String configuration;

    protected KanbanView() {}

    public KanbanView(Long owner, String name, String configuration) {
        this.ownerUserId = owner;
        update(name, configuration);
    }

    public void update(String name, String configuration) {
        this.name = name.trim();
        this.configuration = configuration;
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public String getName() {
        return name;
    }

    public String getConfiguration() {
        return configuration;
    }
}
