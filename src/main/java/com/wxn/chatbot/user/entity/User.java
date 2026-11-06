package com.wxn.chatbot.user.entity;

import com.wxn.chatbot.common.ActiveStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "cpf", length = 11)
    private String cpf;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20, nullable = false)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", length = 20, nullable = false)
    private ActiveStatus accountStatus;

    protected User() {}

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getCpf() {
        return cpf;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public ActiveStatus getAccountStatus() {
        return accountStatus;
    }

    public boolean isRegistrationIncomplete() {
        return username == null || email == null || cpf == null || passwordHash == null;
    }

    public void updateAccount(
        String name,
        String username,
        String email,
        String cpf,
        String phone,
        UserRole role,
        ActiveStatus status
    ) {
        update(name, phone);
        this.username = username;
        this.email = email;
        this.cpf = cpf;
        this.role = role;
        this.accountStatus = status;
    }

    public void initializePassword(String passwordHash) {
        if (this.passwordHash != null) {
            throw new IllegalStateException("Senha já cadastrada");
        }
        this.passwordHash = passwordHash;
    }

    public User(String name, String phone) {
        this.name = name;
        this.phone = phone;
        this.createdAt = this.updatedAt = OffsetDateTime.now();
    }

    public void update(String name, String phone) {
        this.name = name.trim();
        this.phone = phone;
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
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
