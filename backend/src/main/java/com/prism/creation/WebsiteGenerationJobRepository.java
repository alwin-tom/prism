package com.prism.creation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.Modifying;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface WebsiteGenerationJobRepository extends JpaRepository<WebsiteGenerationJob, UUID> {

    Optional<WebsiteGenerationJob> findFirstBySessionIdAndStatusInOrderByCreatedAtDesc(UUID sessionId, Collection<String> statuses);

    Optional<WebsiteGenerationJob> findFirstBySessionIdOrderByCreatedAtDesc(UUID sessionId);

    long countByUserId(UUID userId);

    @Query("select coalesce(sum(job.costUsd), 0) from WebsiteGenerationJob job where job.createdAt >= :start")
    BigDecimal costSince(@Param("start") Instant start);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update WebsiteGenerationJob job
               set job.status = 'CANCELLED', job.completedAt = :now, job.errorMessage = 'The website was deleted.'
             where job.eventId = :eventId
               and job.status in ('QUEUED', 'PLANNING', 'GENERATING', 'VALIDATING', 'REPAIRING')
            """)
    void cancelOpenForEvent(@Param("eventId") UUID eventId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update WebsiteGenerationJob job
               set job.eventId = null, job.sessionId = null
             where job.eventId = :eventId or job.sessionId in :sessionIds
            """)
    void detachEvent(@Param("eventId") UUID eventId, @Param("sessionIds") Collection<UUID> sessionIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update WebsiteGenerationJob job set job.eventId = null where job.eventId = :eventId")
    void detachEvent(@Param("eventId") UUID eventId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update WebsiteGenerationJob job
               set job.status = 'CANCELLED', job.completedAt = :now, job.errorMessage = 'The website was deleted.'
             where job.sessionId = :sessionId
               and job.status in ('QUEUED', 'PLANNING', 'GENERATING', 'VALIDATING', 'REPAIRING')
            """)
    void cancelOpenForSession(@Param("sessionId") UUID sessionId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update WebsiteGenerationJob job set job.sessionId = null where job.sessionId = :sessionId")
    void detachSession(@Param("sessionId") UUID sessionId);
}
