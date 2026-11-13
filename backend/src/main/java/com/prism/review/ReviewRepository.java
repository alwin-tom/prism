package com.prism.review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<EventReview, UUID> {

    List<EventReview> findByEventIdOrderByCreatedAtDesc(UUID eventId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from EventReview r where r.eventId = :eventId")
    void deleteByEventId(@Param("eventId") UUID eventId);
}
