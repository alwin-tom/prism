package com.prism.category;

import com.prism.shared.api.ApiResponse;
import com.prism.shared.exception.ApiException;
import com.prism.template.EventTemplate;
import com.prism.template.TemplateRepository;
import com.prism.template.TemplateVersion;
import com.prism.template.TemplateVersionRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {

    private final CategoryRepository categories;
    private final TemplateRepository templates;
    private final TemplateVersionRepository versions;

    public CatalogController(CategoryRepository categories, TemplateRepository templates, TemplateVersionRepository versions) {
        this.categories = categories;
        this.templates = templates;
        this.versions = versions;
    }

    @GetMapping("/categories")
    public ApiResponse<List<Map<String, Object>>> categories() {
        return ApiResponse.ok(categories.findByActiveTrueOrderByNameAsc().stream().map(this::category).toList());
    }

    @GetMapping("/categories/{id}/templates")
    public ApiResponse<List<Map<String, Object>>> templates(@PathVariable UUID id) {
        return ApiResponse.ok(templates.findByCategoryIdAndActiveTrueOrderByNameAsc(id).stream().map(this::template).toList());
    }

    @GetMapping("/templates")
    public ApiResponse<List<Map<String, Object>>> all() {
        return ApiResponse.ok(templates.findByActiveTrueOrderByNameAsc().stream().map(this::template).toList());
    }

    @GetMapping("/templates/{id}")
    public ApiResponse<Map<String, Object>> one(@PathVariable UUID id) {
        EventTemplate template = templates.findById(id).orElseThrow(() -> ApiException.notFound("That template was not found."));
        return ApiResponse.ok(template(template));
    }

    @GetMapping("/templates/{id}/preview")
    public ApiResponse<Map<String, Object>> preview(@PathVariable UUID id) {
        EventTemplate template = templates.findById(id).orElseThrow(() -> ApiException.notFound("That template was not found."));
        TemplateVersion version = versions.findFirstByTemplateIdAndActiveTrueOrderByVersionNumberDesc(id)
                .orElseThrow(() -> ApiException.notFound("That template has no active version."));
        Map<String, Object> body = template(template);
        body.put("version", version.versionNumber);
        body.put("schema", version.schemaJson);
        body.put("configuration", version.defaultConfiguration);
        return body(body);
    }

    private ApiResponse<Map<String, Object>> body(Map<String, Object> body) {
        return ApiResponse.ok(body);
    }

    private Map<String, Object> category(EventCategory category) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", category.id);
        body.put("name", category.name);
        body.put("code", category.code);
        body.put("description", category.description);
        body.put("thumbnailUrl", category.thumbnailUrl);
        body.put("active", category.active);
        return body;
    }

    private Map<String, Object> template(EventTemplate template) {
        TemplateVersion version = versions.findFirstByTemplateIdAndActiveTrueOrderByVersionNumberDesc(template.id).orElse(null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", template.id);
        body.put("categoryId", template.categoryId);
        body.put("name", template.name);
        body.put("code", template.code);
        body.put("description", template.description);
        body.put("thumbnailUrl", template.thumbnailUrl);
        body.put("active", template.active);
        body.put("version", version == null ? null : version.versionNumber);
        body.put("versionId", version == null ? null : version.id);
        return body;
    }
}
