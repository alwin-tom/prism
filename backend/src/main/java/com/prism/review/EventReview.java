package com.prism.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_reviews")
public class EventReview {

    @Id
    public UUID id;

    @Column(nullable = false)
    public UUID eventId;

    @Column(nullable = false)
    public UUID revisionId;

    @Column(nullable = false)
    public UUID reviewerId;

    @Column(nullable = false, length = 32)
    public String decision;

    public String comments;

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
