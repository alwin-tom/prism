package com.prism.moderation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ModerationAuditLogRepository extends JpaRepository<ModerationAuditLog, UUID> {

    List<ModerationAuditLog> findByReportIdOrderByCreatedAtDesc(UUID reportId);

    Page<ModerationAuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
