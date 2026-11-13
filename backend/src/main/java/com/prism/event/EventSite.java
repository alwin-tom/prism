package com.prism.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "events")
public class EventSite {

    @Id
    public UUID id;

    @Column(nullable = false)
    public UUID userId;

    @Column(nullable = false)
    public UUID categoryId;

    @Column(nullable = false)
    public UUID templateVersionId;

    @Column(nullable = false, length = 160)
    public String eventName;

    @Column(nullable = false, length = 60, unique = true)
    public String slug;

    @Column(nullable = false, length = 32)
    public String status;

    @Column(nullable = false)
    public int requestedDays;

    public UUID approvedRevisionId;

    public UUID publishedRevisionId;

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
        updatedAt = now;
        if (status == null) {
            status = "DRAFT";
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
