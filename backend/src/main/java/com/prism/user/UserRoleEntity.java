package com.prism.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "user_roles")
public class UserRoleEntity {

    @Id
    public UUID id;

    @Column(nullable = false)
    public UUID userId;

    @Column(nullable = false, length = 20)
    public String role;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
