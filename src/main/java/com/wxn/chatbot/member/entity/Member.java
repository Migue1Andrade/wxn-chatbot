package com.wxn.chatbot.member.entity;

import com.wxn.chatbot.common.ActiveStatus;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "members")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "name")
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 20)
    private MemberType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private ActiveStatus status;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    protected Member() {}

    public Member(Long companyId, Long userId, String name, MemberType type, ActiveStatus status) {
        this.companyId = companyId;
        this.userId = userId;
        this.name = name;
        this.type = type;
        this.status = status;
        this.createdAt = this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public MemberType getType() {
        return type;
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
}
