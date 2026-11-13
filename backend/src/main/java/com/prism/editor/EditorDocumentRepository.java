package com.prism.editor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface EditorDocumentRepository extends JpaRepository<EditorDocument, UUID> {
    Optional<EditorDocument> findByRevisionId(UUID revisionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from EditorDocument d where d.eventId = :eventId")
    void deleteByEventId(@Param("eventId") UUID eventId);
}
