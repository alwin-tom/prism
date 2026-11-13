package com.prism.revision;

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
@Table(name = "event_revisions")
public class EventRevision {

    @Id
    public UUID id;

    @Column(nullable = false)
    public UUID eventId;

    @Column(nullable = false)
    public int revisionNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    public JsonNode configurationJson;

    @Column(nullable = false, length = 32)
    public String submissionStatus;

    public Instant submittedAt;

    public Instant approvedAt;

    public UUID approvedBy;

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
        if (submissionStatus == null) {
            submissionStatus = "DRAFT";
        }
    }

    public boolean isImmutable() {
        return !"DRAFT".equals(submissionStatus);
    }
}
