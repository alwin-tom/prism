package com.prism.editor;

import com.fasterxml.jackson.databind.JsonNode;
import com.prism.shared.api.ApiResponse;
import com.prism.shared.config.PrismProperties;
import com.prism.shared.security.AuthContext;
import com.prism.shared.security.UserPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class EditorController {

    private final EditorDocumentService editor;
    private final TypographyEditService typography;
    private final PrismProperties properties;

    public EditorController(EditorDocumentService editor, TypographyEditService typography, PrismProperties properties) {
        this.editor = editor;
        this.typography = typography;
        this.properties = properties;
    }

    @GetMapping("/config/features")
    public ApiResponse<Map<String, Boolean>> features() {
        java.util.Map<String, Boolean> flags = new java.util.LinkedHashMap<>();
        flags.put("imageGenerationEnabled", properties.getImage().isEnabled());
        return ApiResponse.ok(flags);
    }

    @GetMapping("/events/{id}/editor/document")
    public ApiResponse<Map<String, Object>> read(@PathVariable UUID id) {
        return ApiResponse.ok(editor.read(id, AuthContext.require()));
    }

    @PostMapping("/events/{id}/editor/convert")
    public ApiResponse<Map<String, Object>> convert(@PathVariable UUID id) {
        return ApiResponse.ok(editor.convert(id, AuthContext.require()));
    }

    @PutMapping("/events/{id}/editor/document")
    public ApiResponse<Map<String, Object>> save(@PathVariable UUID id, @RequestBody SaveBody body) {
        UserPrincipal principal = AuthContext.require();
        return ApiResponse.ok(editor.save(id, body.document(), body.version(), principal, true));
    }

    @PostMapping("/events/{id}/editor/autosave")
    public ApiResponse<Map<String, Object>> autosave(@PathVariable UUID id, @RequestBody SaveBody body) {
        UserPrincipal principal = AuthContext.require();
        return ApiResponse.ok(editor.save(id, body.document(), body.version(), principal, false));
    }

    @GetMapping("/events/{id}/editor/history")
    public ApiResponse<java.util.List<Map<String, Object>>> history(@PathVariable UUID id) {
        return ApiResponse.ok(editor.history(id, AuthContext.require()));
    }

    @PostMapping("/events/{id}/editor/restore/{versionId}")
    public ApiResponse<Map<String, Object>> restore(@PathVariable UUID id, @PathVariable UUID versionId) {
        return ApiResponse.ok(editor.restore(id, versionId, AuthContext.require()));
    }

    @PostMapping("/events/{id}/editor/validate")
    public ApiResponse<JsonNode> validate(@PathVariable UUID id, @RequestBody JsonNode document) {
        return ApiResponse.ok(editor.validate(id, document, AuthContext.require()));
    }

    @PostMapping("/events/{id}/editor/typography")
    public ApiResponse<Map<String, Object>> typography(@PathVariable UUID id, @RequestBody TypographyBody body) {
        return ApiResponse.ok(typography.preview(id, body.instruction(), AuthContext.require()));
    }

    @GetMapping("/admin/events/{id}/editor/preview")
    public ApiResponse<Map<String, Object>> review(@PathVariable UUID id) {
        return ApiResponse.ok(editor.review(id, AuthContext.require()));
    }

    public record SaveBody(JsonNode document, int version) {
    }

    public record TypographyBody(String instruction) {
    }
}
