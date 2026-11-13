package com.prism.hosting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "hosting_extensions")
public class HostingExtension {

    @Id
    public UUID id;

    @Column(nullable = false)
    public UUID eventId;

    @Column(nullable = false)
    public UUID hostingPeriodId;

    @Column(nullable = false)
    public UUID paymentId;

    @Column(nullable = false)
    public int additionalDays;

    @Column(nullable = false)
    public int amountPaise;

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
