package com.prism.moderation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WebsiteModerationFindingRepository extends JpaRepository<WebsiteModerationFinding, UUID> {

    List<WebsiteModerationFinding> findByReportIdOrderBySeverityAscCreatedAtAsc(UUID reportId);

    void deleteByReportId(UUID reportId);
}
