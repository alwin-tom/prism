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
@Table(name = "whatsapp_messages")
public class WhatsappMessage {

    @Id
    public UUID id;

    public UUID userId;

    @Column(nullable = false, length = 20)
    public String phoneNumber;

    @Column(nullable = false, length = 80)
    public String templateName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    public JsonNode payload;

    @Column(nullable = false, length = 20)
    public String status;

    @Column(length = 128)
    public String providerMessageId;

    @Column(nullable = false)
    public int attempts;

    public String lastError;

    @Column(nullable = false)
    public Instant createdAt;

    public Instant sentAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = "PENDING";
        }
    }
}
