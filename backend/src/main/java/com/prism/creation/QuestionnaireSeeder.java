package com.prism.creation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;

@Component
@Order(20)
public class QuestionnaireSeeder implements ApplicationRunner {

    private final WebsiteCategoryRepository categories;
    private final QuestionnaireDefinitionRepository definitions;
    private final ObjectMapper mapper;

    public QuestionnaireSeeder(WebsiteCategoryRepository categories, QuestionnaireDefinitionRepository definitions, ObjectMapper mapper) {
        this.categories = categories;
        this.definitions = definitions;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        JsonNode catalog;
        try (InputStream input = new ClassPathResource("questionnaires/v1.json").getInputStream()) {
            catalog = mapper.readTree(input);
        }
        for (JsonNode category : catalog.path("categories")) {
            String code = category.path("code").asText("");
            WebsiteCategory row = categories.findByCode(code).orElseGet(() -> {
                WebsiteCategory created = new WebsiteCategory();
                created.code = code;
                created.name = category.path("name").asText(code);
                created.description = category.path("description").asText("");
                created.icon = category.path("icon").asText("custom");
                created.eventCategoryCode = category.path("eventCategory").asText("");
                created.active = true;
                created.displayOrder = category.path("order").asInt(0);
                return categories.save(created);
            });
            if (definitions.findByCategoryIdAndVersion(row.id, 1).isEmpty()) {
                QuestionnaireDefinition definition = new QuestionnaireDefinition();
                definition.categoryId = row.id;
                definition.version = 1;
                definition.name = row.name;
                definition.description = row.description;
                definition.configurationJson = category;
                definition.active = definitions.findByCategoryIdAndActiveTrue(row.id).isEmpty();
                definitions.save(definition);
            }
        }
    }
}
