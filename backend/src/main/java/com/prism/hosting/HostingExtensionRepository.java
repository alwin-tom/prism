package com.prism.hosting;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface HostingExtensionRepository extends JpaRepository<HostingExtension, UUID> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from HostingExtension h where h.eventId = :eventId")
    void deleteByEventId(@Param("eventId") UUID eventId);
}
