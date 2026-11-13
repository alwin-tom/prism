package com.prism.designlibrary;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DesignReferenceRetrievalLogRepository extends JpaRepository<DesignReferenceRetrievalLog, UUID> {

    List<DesignReferenceRetrievalLog> findTop20ByEventCategoryOrderByCreatedAtDesc(String eventCategory);

    List<DesignReferenceRetrievalLog> findTop50ByOrderByCreatedAtDesc();
}
