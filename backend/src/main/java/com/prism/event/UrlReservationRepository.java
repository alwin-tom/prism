package com.prism.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UrlReservationRepository extends JpaRepository<UrlReservation, UUID> {

    Optional<UrlReservation> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from UrlReservation r where r.eventId = :eventId")
    void deleteByEventId(@Param("eventId") UUID eventId);
}
