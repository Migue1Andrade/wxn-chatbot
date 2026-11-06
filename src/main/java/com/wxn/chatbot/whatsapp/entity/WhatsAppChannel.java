package com.wxn.chatbot.whatsapp.entity;

import com.wxn.chatbot.common.ActiveStatus;
import com.wxn.chatbot.whatsapp.model.ProviderType;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "whatsapp_channels")
public class WhatsAppChannel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name")
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 30)
    private ProviderType provider;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "external_channel_id", length = 255)
    private String externalChannelId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private ActiveStatus status;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    protected WhatsAppChannel() {}

    public WhatsAppChannel(
        String name,
        ProviderType provider,
        String phoneNumber,
        String externalChannelId,
        ActiveStatus status
    ) {
        this.name = name;
        this.provider = provider;
        this.phoneNumber = phoneNumber;
        this.externalChannelId = externalChannelId;
        this.status = status;
        this.createdAt = this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public ProviderType getProvider() {
        return provider;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getExternalChannelId() {
        return externalChannelId;
    }

    public ActiveStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public void updatePhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
        this.updatedAt = OffsetDateTime.now();
    }
}
