package com.prism.media;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaRepository extends JpaRepository<EventMedia, UUID> {

    List<EventMedia> findByEventIdOrderByCreatedAtAsc(UUID eventId);

    Optional<EventMedia> findByIdAndEventId(UUID id, UUID eventId);

    List<EventMedia> findByEventIdAndCreatedAtBefore(UUID eventId, Instant before);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from EventMedia m where m.eventId = :eventId")
    void deleteByEventId(@Param("eventId") UUID eventId);
}
