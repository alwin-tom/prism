package com.prism.publishing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PublicationRepository extends JpaRepository<EventPublication, UUID> {

    Optional<EventPublication> findFirstByEventIdAndPublicationStatus(UUID eventId, String publicationStatus);

    List<EventPublication> findByEventIdOrderByPublishedAtDesc(UUID eventId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from EventPublication p where p.eventId = :eventId")
    void deleteByEventId(@Param("eventId") UUID eventId);
}
