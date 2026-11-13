package com.prism.creation;

import com.fasterxml.jackson.databind.JsonNode;
import com.prism.shared.api.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class CreationAdminController {

    private final CreationService creation;
    private final AiRuntime runtime;

    public CreationAdminController(CreationService creation, AiRuntime runtime) {
        this.creation = creation;
        this.runtime = runtime;
    }

    @GetMapping("/website-categories")
    public ApiResponse<List<Map<String, Object>>> categories() {
        return ApiResponse.ok(creation.adminCategories());
    }

    @PutMapping("/website-categories/{code}")
    public ApiResponse<Map<String, Object>> active(@PathVariable String code, @RequestBody ActiveRequest request) {
        return ApiResponse.ok(creation.setCategoryActive(code, request.active()));
    }

    @PutMapping("/questionnaires/{code}")
    public ApiResponse<Map<String, Object>> publish(@PathVariable String code, @RequestBody JsonNode configuration) {
        return ApiResponse.ok(creation.publishQuestionnaire(code, configuration));
    }

    @GetMapping("/ai-models")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> models() {
        return ApiResponse.ok(runtime.view());
    }

    @PutMapping("/ai-models")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> models(@RequestBody JsonNode configuration) {
        return ApiResponse.ok(runtime.update(configuration));
    }

    public record ActiveRequest(boolean active) {
    }
}
