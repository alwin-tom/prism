package com.prism.typography;

import com.fasterxml.jackson.databind.JsonNode;
import com.prism.shared.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class FontController {

    @GetMapping("/fonts")
    public ApiResponse<JsonNode> fonts() {
        return ApiResponse.ok(FontRegistry.root());
    }
}
