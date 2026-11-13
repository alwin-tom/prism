package com.prism.creation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface WebsiteCreationSessionRepository extends JpaRepository<WebsiteCreationSession, UUID> {

    List<WebsiteCreationSession> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    List<WebsiteCreationSession> findByEventId(UUID eventId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from WebsiteCreationSession s where s.eventId = :eventId")
    void deleteByEventId(@Param("eventId") UUID eventId);
}
