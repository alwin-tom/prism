package com.prism.designlibrary;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DesignReferenceSectionRepository extends JpaRepository<DesignReferenceSection, UUID> {

    List<DesignReferenceSection> findByReferenceIdOrderBySortOrderAsc(UUID referenceId);

    void deleteByReferenceId(UUID referenceId);
}
