package com.wxn.chatbot.member.entity;

import com.wxn.chatbot.common.ActiveStatus;
import com.wxn.chatbot.department.entity.Department;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.*;

@Entity
@Table(name = "members")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

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

    @ManyToMany
    @JoinTable(
        name = "member_departments",
        joinColumns = @JoinColumn(name = "member_id"),
        inverseJoinColumns = @JoinColumn(name = "department_id")
    )
    private Set<Department> departments = new LinkedHashSet<>();

    public Set<Department> getDepartments() {
        return Collections.unmodifiableSet(departments);
    }

    public void update(String name, ActiveStatus status, Set<Department> departments) {
        this.name = name.trim();
        this.status = status;
        this.departments.clear();
        this.departments.addAll(departments);
        this.updatedAt = OffsetDateTime.now();
    }

    protected Member() {}

    public Member(Long userId, String name, MemberType type, ActiveStatus status) {
        this.userId = userId;
        this.name = name;
        this.type = type;
        this.status = status;
        this.createdAt = this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
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
