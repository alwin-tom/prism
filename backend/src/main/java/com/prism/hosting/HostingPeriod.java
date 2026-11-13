package com.prism.hosting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "hosting_periods")
public class HostingPeriod {

    @Id
    public UUID id;

    @Column(nullable = false)
    public UUID eventId;

    @Column(nullable = false)
    public UUID paymentId;

    @Column(name = "start_at", nullable = false)
    public Instant startAt;

    @Column(name = "end_at", nullable = false)
    public Instant endAt;

    @Column(nullable = false)
    public int days;

    @Column(nullable = false)
    public int dailyRatePaise;

    @Column(nullable = false, length = 20)
    public String status;

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
