package com.prism.rsvp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_rsvps")
public class EventRsvp {

    @Id
    public UUID id;

    @Column(nullable = false)
    public UUID eventId;

    @Column(nullable = false, length = 160)
    public String guestName;

    @Column(length = 20)
    public String guestPhone;

    @Column(length = 254)
    public String guestEmail;

    @Column(nullable = false, length = 20)
    public String attendanceStatus;

    @Column(nullable = false)
    public int numberOfGuests;

    public String message;

    @Column(nullable = false)
    public Instant submittedAt;

    @Column(length = 64)
    public String requestIp;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (submittedAt == null) {
            submittedAt = Instant.now();
        }
    }
}
