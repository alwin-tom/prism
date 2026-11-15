package com.prism.imagegen;

import com.fasterxml.jackson.databind.JsonNode;
import com.prism.shared.api.ApiResponse;
import com.prism.shared.security.AuthContext;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ImageGenerationController {

    private final GeneratedAssetService assets;

    public ImageGenerationController(GeneratedAssetService assets) {
        this.assets = assets;
    }

    @GetMapping("/events/{id}/generated-assets")
    public ApiResponse<List<Map<String, Object>>> list(@PathVariable UUID id) {
        return ApiResponse.ok(assets.list(id, AuthContext.require()));
    }

    @GetMapping("/events/{id}/generated-assets/{assetId}")
    public ApiResponse<Map<String, Object>> get(@PathVariable UUID id, @PathVariable UUID assetId) {
        return ApiResponse.ok(assets.get(id, assetId, AuthContext.require()));
    }

    @PostMapping("/events/{id}/generated-assets/generate")
    public ApiResponse<Map<String, Object>> generate(@PathVariable UUID id, @RequestBody JsonNode body) {
        return ApiResponse.ok(assets.generate(id, body, AuthContext.require()));
    }

    @PostMapping("/events/{id}/generated-assets/{assetId}/regenerate")
    public ApiResponse<Map<String, Object>> regenerate(@PathVariable UUID id, @PathVariable UUID assetId) {
        return ApiResponse.ok(assets.regenerate(id, assetId, AuthContext.require()));
    }

    @DeleteMapping("/events/{id}/generated-assets/{assetId}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable UUID id, @PathVariable UUID assetId) throws IOException {
        assets.delete(id, assetId, AuthContext.require());
        return ApiResponse.ok(Map.of("deleted", true));
    }

    @GetMapping("/events/{id}/image-generation/jobs")
    public ApiResponse<List<Map<String, Object>>> jobs(@PathVariable UUID id) {
        return ApiResponse.ok(assets.listJobs(id, AuthContext.require()));
    }

    @GetMapping("/events/{id}/image-generation/jobs/{jobId}")
    public ApiResponse<Map<String, Object>> job(@PathVariable UUID id, @PathVariable UUID jobId) {
        return ApiResponse.ok(assets.getJob(id, jobId, AuthContext.require()));
    }

    @GetMapping("/image-generation/models")
    public ApiResponse<List<Map<String, Object>>> models() {
        AuthContext.require();
        return ApiResponse.ok(assets.models());
    }

    @GetMapping("/image-generation/status")
    public ApiResponse<Map<String, Object>> status() {
        AuthContext.require();
        return ApiResponse.ok(assets.status());
    }
}
