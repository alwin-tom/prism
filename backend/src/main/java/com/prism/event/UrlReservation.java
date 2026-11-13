package com.prism.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "url_reservations")
public class UrlReservation {

    @Id
    public UUID id;

    @Column(nullable = false, length = 60, unique = true)
    public String slug;

    public UUID eventId;

    @Column(nullable = false)
    public UUID userId;

    @Column(nullable = false)
    public Instant createdAt;

    public Instant expiresAt;

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
