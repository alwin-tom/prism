package com.prism.moderation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "moderation_audit_logs")
public class ModerationAuditLog {

    @Id
    public UUID id;

    @Column(nullable = false)
    public UUID reportId;

    public UUID findingId;

    @Column(nullable = false)
    public UUID administratorId;

    @Column(nullable = false, length = 64)
    public String action;

    @Column(columnDefinition = "TEXT")
    public String previousValue;

    @Column(columnDefinition = "TEXT")
    public String newValue;

    @Column(columnDefinition = "TEXT")
    public String note;

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
