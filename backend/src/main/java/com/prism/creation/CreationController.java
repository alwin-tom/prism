package com.prism.creation;

import com.fasterxml.jackson.databind.JsonNode;
import com.prism.shared.api.ApiResponse;
import com.prism.shared.security.AuthContext;
import com.prism.shared.security.UserPrincipal;
import com.prism.shared.util.Requests;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class CreationController {

    private final CreationService creation;

    public CreationController(CreationService creation) {
        this.creation = creation;
    }

    @GetMapping("/website-categories")
    public ApiResponse<List<Map<String, Object>>> categories() {
        AuthContext.require();
        return ApiResponse.ok(creation.categories());
    }

    @GetMapping("/website-categories/{code}")
    public ApiResponse<Map<String, Object>> category(@PathVariable String code) {
        AuthContext.require();
        return ApiResponse.ok(creation.category(code));
    }

    @GetMapping("/questionnaires/{code}")
    public ApiResponse<Map<String, Object>> questionnaire(@PathVariable String code, @RequestParam(required = false) Integer version) {
        AuthContext.require();
        return ApiResponse.ok(creation.questionnaire(code, version));
    }

    @GetMapping("/website-creation-sessions")
    public ApiResponse<List<Map<String, Object>>> sessions() {
        return ApiResponse.ok(creation.sessions(AuthContext.require()));
    }

    @PostMapping("/website-creation-sessions")
    public ApiResponse<Map<String, Object>> start(@RequestBody StartRequest request, HttpServletRequest http) {
        UserPrincipal principal = AuthContext.require();
        return ApiResponse.ok(creation.start(principal, request.categoryCode(), Requests.clientIp(http)));
    }

    @DeleteMapping("/website-creation-sessions/{id}")
    public ApiResponse<Void> discard(@PathVariable UUID id, HttpServletRequest http) {
        UserPrincipal principal = AuthContext.require();
        creation.discard(id, principal, Requests.clientIp(http));
        return ApiResponse.ok(null);
    }

    @GetMapping("/website-creation-sessions/{id}")
    public ApiResponse<Map<String, Object>> session(@PathVariable UUID id) {
        return ApiResponse.ok(creation.session(id, AuthContext.require()));
    }

    @PutMapping("/website-creation-sessions/{id}/answers")
    public ApiResponse<Map<String, Object>> answers(@PathVariable UUID id, @RequestBody AnswerRequest request) {
        return ApiResponse.ok(creation.saveAnswers(id, request.answers(), request.step(), AuthContext.require()));
    }

    @PutMapping("/website-creation-sessions/{id}/progress")
    public ApiResponse<Map<String, Object>> progress(@PathVariable UUID id, @RequestBody ProgressRequest request) {
        return ApiResponse.ok(creation.progress(id, request.step(), AuthContext.require()));
    }

    @PostMapping("/website-creation-sessions/{id}/complete")
    public ApiResponse<Map<String, Object>> complete(@PathVariable UUID id) {
        return ApiResponse.ok(creation.complete(id, AuthContext.require()));
    }

    @PostMapping("/website-creation-sessions/{id}/generate")
    public ApiResponse<Map<String, Object>> generate(@PathVariable UUID id, @RequestBody(required = false) GenerateRequest request) {
        boolean confirm = request != null && request.confirmReplace();
        return ApiResponse.ok(creation.generate(id, confirm, AuthContext.require()));
    }

    @GetMapping("/website-generation-jobs/{id}")
    public ApiResponse<Map<String, Object>> job(@PathVariable UUID id) {
        return ApiResponse.ok(creation.job(id, AuthContext.require()));
    }

    @PostMapping("/website-generation-jobs/{id}/retry")
    public ApiResponse<Map<String, Object>> retry(@PathVariable UUID id) {
        return ApiResponse.ok(creation.retry(id, AuthContext.require()));
    }

    @PostMapping("/website-generation-jobs/{id}/cancel")
    public ApiResponse<Map<String, Object>> cancel(@PathVariable UUID id) {
        return ApiResponse.ok(creation.cancel(id, AuthContext.require()));
    }

    @GetMapping("/website-generation-jobs/{id}/result")
    public ApiResponse<Map<String, Object>> result(@PathVariable UUID id) {
        return ApiResponse.ok(creation.result(id, AuthContext.require()));
    }

    @PostMapping("/website-creation-sessions/{id}/open-editor")
    public ApiResponse<Map<String, Object>> editor(@PathVariable UUID id) {
        return ApiResponse.ok(creation.openEditor(id, AuthContext.require()));
    }

    public record StartRequest(String categoryCode) {
    }

    public record AnswerRequest(JsonNode answers, String step) {
    }

    public record ProgressRequest(String step) {
    }

    public record GenerateRequest(boolean confirmReplace) {
    }
}
