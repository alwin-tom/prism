package com.prism.event;

import com.prism.shared.api.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class PublicController {

    private final PublicEventService publicEvents;
    private final PublicHtmlRenderer html;

    public PublicController(PublicEventService publicEvents, PublicHtmlRenderer html) {
        this.publicEvents = publicEvents;
        this.html = html;
    }

    @GetMapping("/api/v1/public/events/{slug}")
    public ApiResponse<Map<String, Object>> event(@PathVariable String slug) {
        return ApiResponse.ok(publicEvents.resolve(slug, true));
    }

    @GetMapping(value = "/render/{slug}", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String page(@PathVariable String slug) {
        return html.render(slug);
    }
}
