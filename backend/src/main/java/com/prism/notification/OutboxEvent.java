package com.prism.notification;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    public UUID id;

    @Column(nullable = false, length = 80)
    public String aggregateType;

    @Column(nullable = false)
    public UUID aggregateId;

    @Column(nullable = false, length = 80)
    public String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    public JsonNode payload;

    @Column(nullable = false, length = 20)
    public String status;

    @Column(nullable = false)
    public int attempts;

    @Column(nullable = false)
    public Instant availableAt;

    @Column(nullable = false)
    public Instant createdAt;

    public Instant processedAt;

    public String lastError;

    @Column(length = 200)
    public String dedupeKey;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (availableAt == null) {
            availableAt = now;
        }
        if (status == null) {
            status = "PENDING";
        }
    }
}
