package com.prism.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findTop20ByStatusAndAvailableAtBeforeOrderByCreatedAtAsc(String status, Instant availableAt);

    boolean existsByDedupeKey(String dedupeKey);
}
