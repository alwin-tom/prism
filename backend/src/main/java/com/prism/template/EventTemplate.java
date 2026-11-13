package com.prism.template;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "templates")
public class EventTemplate {

    @Id
    public UUID id;

    @Column(nullable = false)
    public UUID categoryId;

    @Column(nullable = false, length = 120)
    public String name;

    @Column(nullable = false, length = 60, unique = true)
    public String code;

    @Column(nullable = false)
    public String description;

    public String thumbnailUrl;

    @Column(nullable = false)
    public boolean active;

    @Column(nullable = false)
    public Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
