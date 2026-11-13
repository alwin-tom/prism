package com.prism.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    public UUID id;

    @Column(nullable = false)
    public UUID eventId;

    @Column(nullable = false, length = 32)
    public String paymentType;

    @Column(nullable = false, length = 32)
    public String gateway;

    @Column(length = 80)
    public String gatewayOrderId;

    @Column(length = 80)
    public String gatewayPaymentId;

    @Column(nullable = false)
    public int amountPaise;

    @Column(nullable = false)
    public int days;

    @Column(nullable = false)
    public int dailyRatePaise;

    @Column(nullable = false, length = 3)
    public String currency;

    @Column(nullable = false, length = 20)
    public String status;

    public Instant verifiedAt;

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
