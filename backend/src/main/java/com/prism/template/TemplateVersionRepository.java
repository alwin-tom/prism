package com.prism.template;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TemplateVersionRepository extends JpaRepository<TemplateVersion, UUID> {

    Optional<TemplateVersion> findFirstByTemplateIdAndActiveTrueOrderByVersionNumberDesc(UUID templateId);

    List<TemplateVersion> findByTemplateIdOrderByVersionNumberDesc(UUID templateId);

    Optional<TemplateVersion> findByTemplateIdAndVersionNumber(UUID templateId, int versionNumber);
}
