package com.prism.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "otp_verifications")
public class OtpVerification {

    @Id
    public UUID id;

    @Column(nullable = false, length = 20)
    public String phoneNumber;

    @Column(nullable = false, length = 128)
    public String otpHash;

    @Column(nullable = false)
    public Instant expiresAt;

    @Column(nullable = false)
    public int attempts;

    public Instant verifiedAt;

    @Column(length = 64)
    public String requestIp;

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
