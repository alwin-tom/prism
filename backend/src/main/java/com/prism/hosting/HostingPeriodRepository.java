package com.prism.hosting;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HostingPeriodRepository extends JpaRepository<HostingPeriod, UUID> {

    List<HostingPeriod> findByEventIdOrderByStartAtAsc(UUID eventId);

    List<HostingPeriod> findByStatusAndEndAtBefore(String status, Instant instant);

    @Query("""
            select h from HostingPeriod h
            where h.eventId = :eventId
              and h.status in ('ACTIVE', 'SCHEDULED')
              and h.endAt = (
                  select max(h2.endAt) from HostingPeriod h2
                  where h2.eventId = :eventId and h2.status in ('ACTIVE', 'SCHEDULED')
              )
            """)
    Optional<HostingPeriod> findLatestOpen(UUID eventId);

    @Query("""
            select h from HostingPeriod h
            where h.status = 'ACTIVE' and h.endAt > :now and h.endAt <= :until
            """)
    List<HostingPeriod> findEndingBetween(Instant now, Instant until);

    long countByStatus(String status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from HostingPeriod h where h.eventId = :eventId")
    void deleteByEventId(@Param("eventId") UUID eventId);
}
