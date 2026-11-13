package com.prism.designlibrary;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DesignReferenceMediaRepository extends JpaRepository<DesignReferenceMedia, UUID> {
}
