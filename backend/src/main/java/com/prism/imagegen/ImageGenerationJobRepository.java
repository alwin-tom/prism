package com.prism.imagegen;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImageGenerationJobRepository extends JpaRepository<ImageGenerationJob, UUID> {

    List<ImageGenerationJob> findByEventIdOrderByCreatedAtDesc(UUID eventId);

    List<ImageGenerationJob> findByWebsiteGenerationJobIdOrderByCreatedAtAsc(UUID websiteGenerationJobId);

    Optional<ImageGenerationJob> findByIdAndEventId(UUID id, UUID eventId);

    Optional<ImageGenerationJob> findByIdempotencyKey(String idempotencyKey);

    List<ImageGenerationJob> findByStatusInOrderByUpdatedAtAsc(Collection<String> statuses);

    long countByEventIdAndStatusIn(UUID eventId, Collection<String> statuses);
}
