package com.prism.event;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<EventSite, UUID> {

    Page<EventSite> findByUserId(UUID userId, Pageable pageable);

    Optional<EventSite> findBySlug(String slug);

    boolean existsBySlug(String slug);

    long countByStatus(String status);

    long countByUserId(UUID userId);

    long countByUserIdAndStatus(UUID userId, String status);

    Page<EventSite> findByStatus(String status, Pageable pageable);

    java.util.List<EventSite> findAllByStatus(String status);

    java.util.List<EventSite> findByStatusAndUpdatedAtBefore(String status, java.time.Instant updatedAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EventSite e where e.id = :id")
    Optional<EventSite> lockById(UUID id);

    @Query("""
            select e from EventSite e
            where (:status is null or e.status = :status)
              and (
                   :query is null
                or lower(e.eventName) like lower(concat('%', :query, '%'))
                or lower(e.slug) like lower(concat('%', :query, '%'))
              )
            """)
    Page<EventSite> search(String status, String query, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update EventSite e set e.approvedRevisionId = null, e.publishedRevisionId = null where e.id = :id")
    void clearRevisionPointers(@Param("id") UUID id);
}
