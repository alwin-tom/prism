package com.prism.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OtpRepository extends JpaRepository<OtpVerification, UUID> {

    Optional<OtpVerification> findFirstByPhoneNumberAndVerifiedAtIsNullOrderByCreatedAtDesc(String phoneNumber);

    long countByPhoneNumberAndCreatedAtAfter(String phoneNumber, Instant after);

    long countByRequestIpAndCreatedAtAfter(String requestIp, Instant after);

    @Modifying
    @Query("""
            update OtpVerification o
               set o.expiresAt = :now
             where o.phoneNumber = :phone
               and o.verifiedAt is null
               and o.expiresAt > :now
            """)
    int expireOpen(String phone, Instant now);
}
