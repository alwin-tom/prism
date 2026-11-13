package com.prism.revision;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RevisionRepository extends JpaRepository<EventRevision, UUID> {

    Optional<EventRevision> findFirstByEventIdOrderByRevisionNumberDesc(UUID eventId);

    List<EventRevision> findByEventIdOrderByRevisionNumberAsc(UUID eventId);

    Page<EventRevision> findBySubmissionStatusInOrderBySubmittedAtAsc(Collection<String> statuses, Pageable pageable);

    long countBySubmissionStatusIn(Collection<String> statuses);

    List<EventRevision> findBySubmittedAtAfter(java.time.Instant submittedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from EventRevision r where r.eventId = :eventId")
    void deleteByEventId(@Param("eventId") UUID eventId);
}
