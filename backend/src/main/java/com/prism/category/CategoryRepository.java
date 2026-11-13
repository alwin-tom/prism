package com.prism.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<EventCategory, UUID> {

    List<EventCategory> findByActiveTrueOrderByNameAsc();

    Optional<EventCategory> findByCode(String code);
}
