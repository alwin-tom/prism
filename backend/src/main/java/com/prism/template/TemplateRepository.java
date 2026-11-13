package com.prism.template;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TemplateRepository extends JpaRepository<EventTemplate, UUID> {

    List<EventTemplate> findByActiveTrueOrderByNameAsc();

    List<EventTemplate> findByCategoryIdAndActiveTrueOrderByNameAsc(UUID categoryId);

    List<EventTemplate> findByCategoryIdOrderByNameAsc(UUID categoryId);

    Optional<EventTemplate> findByCode(String code);

    long countByCategoryId(UUID categoryId);
}
