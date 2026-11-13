package com.prism.creation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "website_categories")
public class WebsiteCategory {

    @Id
    public UUID id;

    @Column(nullable = false, length = 40, unique = true)
    public String code;

    @Column(nullable = false, length = 80)
    public String name;

    @Column(nullable = false)
    public String description;

    @Column(nullable = false, length = 40)
    public String icon;

    @Column(nullable = false, length = 40)
    public String eventCategoryCode;

    @Column(nullable = false)
    public boolean active;

    @Column(nullable = false)
    public int displayOrder;

    @Column(nullable = false)
    public Instant createdAt;

    @Column(nullable = false)
    public Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }
}
