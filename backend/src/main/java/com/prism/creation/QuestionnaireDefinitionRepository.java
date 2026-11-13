package com.prism.creation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface QuestionnaireDefinitionRepository extends JpaRepository<QuestionnaireDefinition, UUID> {

    Optional<QuestionnaireDefinition> findByCategoryIdAndActiveTrue(UUID categoryId);

    Optional<QuestionnaireDefinition> findByCategoryIdAndVersion(UUID categoryId, int version);

    Optional<QuestionnaireDefinition> findFirstByCategoryIdOrderByVersionDesc(UUID categoryId);
}
