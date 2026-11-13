package com.prism.designlibrary;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "design_reference_sections")
public class DesignReferenceSection {

    @Id
    public UUID id;

    @Column(nullable = false)
    public UUID referenceId;

    @Column(nullable = false, length = 80)
    public String sectionKey;

    @Column(nullable = false, columnDefinition = "text")
    public String purpose = "";

    @Column(nullable = false, length = 120)
    public String composition = "";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    public JsonNode patternJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    public JsonNode componentMap;

    @Column(nullable = false)
    public int sortOrder;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
