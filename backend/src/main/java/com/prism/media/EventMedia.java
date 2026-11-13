package com.prism.media;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_media")
public class EventMedia {

    @Id
    public UUID id;

    @Column(nullable = false)
    public UUID eventId;

    public UUID revisionId;

    @Column(nullable = false)
    public String originalFilename;

    @Column(nullable = false)
    public String storedFilename;

    @Column(nullable = false, length = 20)
    public String mediaType;

    @Column(nullable = false, length = 80)
    public String mimeType;

    @Column(nullable = false)
    public long fileSize;

    @Column(nullable = false)
    public String storagePath;

    public String thumbnailPath;

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
