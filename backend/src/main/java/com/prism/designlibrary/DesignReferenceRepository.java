package com.prism.designlibrary;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DesignReferenceRepository extends JpaRepository<DesignReference, UUID> {

    Optional<DesignReference> findBySeedKey(String seedKey);

    boolean existsBySeedKey(String seedKey);

    List<DesignReference> findByStatusOrderByQualityScoreDescNameAsc(String status);

    List<DesignReference> findByStatusAndEventCategoryOrderByQualityScoreDescNameAsc(String status, String eventCategory);

    @Query("""
            select r from DesignReference r
            where r.status = :status
              and (:category = '' or r.eventCategory = :category or r.eventCategory = 'GENERAL')
              and (:archetype = '' or r.archetypeId = :archetype)
              and (:sourceType = '' or r.sourceType = :sourceType)
              and (
                :q = ''
                or lower(r.name) like lower(concat('%', :q, '%'))
                or lower(r.description) like lower(concat('%', :q, '%'))
              )
            order by r.qualityScore desc, r.name asc
            """)
    List<DesignReference> search(
            @Param("status") String status,
            @Param("category") String category,
            @Param("archetype") String archetype,
            @Param("sourceType") String sourceType,
            @Param("q") String q
    );

    long countByStatus(String status);

    long countBySourceType(String sourceType);

    @Query("select r.eventCategory, count(r) from DesignReference r group by r.eventCategory")
    List<Object[]> countByCategory();

    @Query("select r.archetypeId, count(r) from DesignReference r group by r.archetypeId")
    List<Object[]> countByArchetype();

    @Query("select r from DesignReference r where r.status = 'APPROVED' and r.sourceType <> 'INSPIRATION_LINK' order by r.retrievalCount desc, r.qualityScore desc")
    List<DesignReference> mostRetrieved();
}
