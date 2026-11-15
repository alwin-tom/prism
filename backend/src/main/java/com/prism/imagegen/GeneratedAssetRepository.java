package com.prism.imagegen;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GeneratedAssetRepository extends JpaRepository<GeneratedAsset, UUID> {

    List<GeneratedAsset> findByEventIdOrderByCreatedAtDesc(UUID eventId);

    Optional<GeneratedAsset> findByIdAndEventId(UUID id, UUID eventId);

    Optional<GeneratedAsset> findFirstByEventIdAndAssetKeyOrderByVersionDesc(UUID eventId, String assetKey);

    @Query("""
            select coalesce(sum(a.actualCostUsd), 0) from GeneratedAsset a
            where a.eventId = :eventId and a.status = 'COMPLETED' and a.actualCostUsd is not null
            """)
    BigDecimal sumActualCost(@Param("eventId") UUID eventId);

    long countByEventIdAndStatusIn(UUID eventId, List<String> statuses);
}
