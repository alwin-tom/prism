package com.prism.creation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebsiteCategoryRepository extends JpaRepository<WebsiteCategory, UUID> {

    List<WebsiteCategory> findByActiveTrueOrderByDisplayOrderAsc();

    List<WebsiteCategory> findAllByOrderByDisplayOrderAsc();

    Optional<WebsiteCategory> findByCode(String code);
}
