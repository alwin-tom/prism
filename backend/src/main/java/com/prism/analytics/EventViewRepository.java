package com.prism.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface EventViewRepository extends JpaRepository<EventView, EventView.Key> {

    @Query("select coalesce(sum(v.viewCount), 0) from EventView v where v.id.eventId = :eventId")
    long totalForEvent(UUID eventId);

    @Query("""
            select coalesce(sum(v.viewCount), 0) from EventView v
            where v.id.eventId = :eventId and v.id.viewedOn >= :from
            """)
    long totalForEventSince(UUID eventId, LocalDate from);

    List<EventView> findByIdEventIdAndIdViewedOnGreaterThanEqualOrderByIdViewedOnAsc(UUID eventId, LocalDate from);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from EventView v where v.id.eventId = :eventId")
    void deleteByEventId(@Param("eventId") UUID eventId);
}
