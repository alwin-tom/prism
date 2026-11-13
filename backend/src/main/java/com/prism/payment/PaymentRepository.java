package com.prism.payment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByEventIdOrderByCreatedAtDesc(UUID eventId);

    List<Payment> findByEventIdInOrderByCreatedAtDesc(java.util.Collection<UUID> eventIds);

    Optional<Payment> findByGatewayAndGatewayOrderId(String gateway, String gatewayOrderId);

    List<Payment> findByStatusAndCreatedAtBefore(String status, Instant before);

    Page<Payment> findByStatus(String status, Pageable pageable);

    @Query("""
            select coalesce(sum(p.amountPaise), 0) from Payment p
            where p.status = 'PAID' and p.createdAt >= :from and p.createdAt < :to
            """)
    long sumPaidBetween(Instant from, Instant to);

    Page<Payment> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Payment p where p.eventId = :eventId")
    void deleteByEventId(@Param("eventId") UUID eventId);
}
