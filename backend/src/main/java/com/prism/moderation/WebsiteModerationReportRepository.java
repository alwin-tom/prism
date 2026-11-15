package com.prism.moderation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebsiteModerationReportRepository extends JpaRepository<WebsiteModerationReport, UUID> {

    Optional<WebsiteModerationReport> findByRevisionId(UUID revisionId);

    List<WebsiteModerationReport> findByEventIdOrderByCreatedAtDesc(UUID eventId);

    List<WebsiteModerationReport> findByStatusInOrderByUpdatedAtAsc(Collection<String> statuses);

    @Query("""
            select r from WebsiteModerationReport r
            where (:status is null or r.status = :status)
              and (:risk is null or r.overallRisk = :risk)
            order by r.createdAt desc
            """)
    Page<WebsiteModerationReport> search(@Param("status") String status, @Param("risk") String risk, Pageable pageable);

    long countByStatus(String status);

    long countByOverallRiskAndStatus(String overallRisk, String status);
}
