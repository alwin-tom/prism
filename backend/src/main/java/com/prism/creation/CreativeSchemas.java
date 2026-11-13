package com.prism.creation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class CreativeSchemas {

    public static final String PROMPT_VERSION = "webaux-creative-v2";
    public static final String DIRECTION_SCHEMA = "webaux_creative_direction";
    public static final String BLUEPRINT_SCHEMA = "webaux_visual_blueprint";
    public static final String DIRECTOR_SCHEMA = "webaux_design_director";

    private final JsonNode direction;
    private final JsonNode blueprint;
    private final JsonNode director;

    public CreativeSchemas(ObjectMapper mapper) {
        this.direction = read(mapper, "creation/creative-direction.schema.json");
        this.blueprint = read(mapper, "creation/visual-blueprint.schema.json");
        this.director = read(mapper, "creation/design-director.schema.json");
    }

    public JsonNode direction() {
        return direction;
    }

    public JsonNode blueprint() {
        return blueprint;
    }

    public JsonNode director() {
        return director;
    }

    private static JsonNode read(ObjectMapper mapper, String path) {
        try (InputStream input = new ClassPathResource(path).getInputStream()) {
            return mapper.readTree(input);
        } catch (Exception exception) {
            throw new IllegalStateException("Creative schema missing: " + path, exception);
        }
    }
}
