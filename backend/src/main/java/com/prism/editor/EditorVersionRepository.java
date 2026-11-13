package com.prism.editor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface EditorVersionRepository extends JpaRepository<EditorDocumentVersion, UUID> {
    List<EditorDocumentVersion> findByDocumentIdOrderByVersionNumberDesc(UUID documentId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from EditorDocumentVersion v where v.documentId in (select d.id from EditorDocument d where d.eventId = :eventId)")
    void deleteByEventId(@Param("eventId") UUID eventId);
}
