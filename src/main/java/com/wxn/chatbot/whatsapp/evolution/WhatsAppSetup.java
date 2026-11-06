package com.wxn.chatbot.whatsapp.evolution;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "whatsapp_setup")
public class WhatsAppSetup {

    @Id
    private Integer id;

    @Column(name = "channel_id")
    private Long channelId;

    @Column(nullable = false)
    private boolean completed;

    @Column(name = "connected_phone", length = 40)
    private String connectedPhone;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected WhatsAppSetup() {}

    public WhatsAppSetup(Integer id) {
        this.id = id;
        this.updatedAt = OffsetDateTime.now();
    }

    public Integer getId() {
        return id;
    }

    public Long getChannelId() {
        return channelId;
    }

    public void setChannelId(Long channelId) {
        this.channelId = channelId;
        updatedAt = OffsetDateTime.now();
    }

    public boolean isCompleted() {
        return completed;
    }

    public String getConnectedPhone() {
        return connectedPhone;
    }

    public void complete(String phone) {
        completed = true;
        connectedPhone = phone;
        completedAt = OffsetDateTime.now();
        updatedAt = completedAt;
    }
}
