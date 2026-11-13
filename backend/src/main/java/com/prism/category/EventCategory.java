package com.prism.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_categories")
public class EventCategory {

    @Id
    public UUID id;

    @Column(nullable = false, length = 80)
    public String name;

    @Column(nullable = false, length = 40, unique = true)
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
