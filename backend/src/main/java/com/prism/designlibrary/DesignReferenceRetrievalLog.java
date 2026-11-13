package com.prism.designlibrary;

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
@Table(name = "design_reference_retrieval_log")
public class DesignReferenceRetrievalLog {

    @Id
    public UUID id;

    public UUID jobId;

    public UUID userId;

    @Column(length = 40)
    public String eventCategory;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "uuid[]")
    public UUID[] referenceIds = new UUID[0];

    @Column(nullable = false, length = 20)
    public String retrievalMode = "sql";

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
