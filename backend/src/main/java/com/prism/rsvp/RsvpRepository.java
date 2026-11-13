package com.prism.rsvp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RsvpRepository extends JpaRepository<EventRsvp, UUID> {

    List<EventRsvp> findByEventIdOrderBySubmittedAtDesc(UUID eventId);

    Optional<EventRsvp> findByIdAndEventId(UUID id, UUID eventId);

    long countByEventIdAndRequestIpAndSubmittedAtAfter(UUID eventId, String requestIp, Instant after);

    @Query("select coalesce(sum(r.numberOfGuests), 0) from EventRsvp r where r.eventId = :eventId and r.attendanceStatus = 'ACCEPTED'")
    long sumAcceptedGuests(UUID eventId);

    long countByEventIdAndAttendanceStatus(UUID eventId, String attendanceStatus);

    long countByEventId(UUID eventId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from EventRsvp r where r.eventId = :eventId")
    void deleteByEventId(@Param("eventId") UUID eventId);
}
