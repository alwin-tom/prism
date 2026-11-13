package com.prism.designlibrary;

import com.fasterxml.jackson.databind.JsonNode;
import com.prism.shared.api.ApiResponse;
import com.prism.shared.security.AuthContext;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/design-references")
public class DesignReferenceAdminController {

    private final DesignReferenceService references;
    private final DesignReferenceMediaService media;
    private final DesignReferenceEmbeddingService embeddings;

    public DesignReferenceAdminController(
            DesignReferenceService references,
            DesignReferenceMediaService media,
            DesignReferenceEmbeddingService embeddings
    ) {
        this.references = references;
        this.media = media;
        this.embeddings = embeddings;
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        return ApiResponse.ok(references.dashboard());
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String archetype,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String q
    ) {
        return ApiResponse.ok(references.list(status, category, archetype, sourceType, q));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable UUID id) {
        return ApiResponse.ok(references.get(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> create(@RequestBody JsonNode body) {
        return ApiResponse.ok(references.create(body, AuthContext.require()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> update(@PathVariable UUID id, @RequestBody JsonNode body) {
        return ApiResponse.ok(references.update(id, body));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> status(@PathVariable UUID id, @RequestBody StatusRequest request) {
        return ApiResponse.ok(references.setStatus(id, request.status()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        references.delete(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/screenshots")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> upload(
            @PathVariable UUID id,
            @RequestParam String kind,
            @RequestPart("file") MultipartFile file
    ) throws IOException {
        return ApiResponse.ok(references.uploadScreenshot(id, kind, file));
    }

    @GetMapping("/media/{mediaId}")
    public ResponseEntity<Resource> media(@PathVariable UUID mediaId) {
        DesignReferenceMedia row = media.require(mediaId);
        Resource resource = media.resource(row);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + row.storedFilename + "\"")
                .contentType(MediaType.parseMediaType(row.mimeType))
                .body(resource);
    }

    @PostMapping("/{id}/embed")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> embed(@PathVariable UUID id) {
        return ApiResponse.ok(embeddings.embedOne(id));
    }

    @PostMapping("/embed-missing")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> embedMissing() {
        return ApiResponse.ok(embeddings.embedMissing(50));
    }

    public record StatusRequest(String status) {
    }
}
