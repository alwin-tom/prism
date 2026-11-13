package com.prism.designlibrary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "design_reference_media")
public class DesignReferenceMedia {

    @Id
    public UUID id;

    @Column(nullable = false, length = 260)
    public String originalFilename;

    @Column(nullable = false, length = 80)
    public String storedFilename;

    @Column(nullable = false, length = 80)
    public String mimeType;

    @Column(nullable = false)
    public long fileSize;

    @Column(nullable = false)
    public String storagePath;

    @Column(nullable = false, length = 20)
    public String kind;

    public Integer width;

    public Integer height;

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
