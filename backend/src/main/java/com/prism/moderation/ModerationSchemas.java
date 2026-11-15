package com.prism.moderation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class ModerationSchemas {

    private ModerationSchemas() {
    }

    static JsonNode result(ObjectMapper mapper) {
        return read(mapper, """
                {
                  "type": "object",
                  "additionalProperties": false,
                  "required": ["overallRisk", "summary", "findings", "explanation"],
                  "properties": {
                    "overallRisk": {"type": "string", "enum": ["CRITICAL", "HIGH", "MEDIUM", "LOW", "CLEAN"]},
                    "summary": {
                      "type": "object",
                      "additionalProperties": false,
                      "required": ["critical", "high", "medium", "low", "clean"],
                      "properties": {
                        "critical": {"type": "integer", "minimum": 0},
                        "high": {"type": "integer", "minimum": 0},
                        "medium": {"type": "integer", "minimum": 0},
                        "low": {"type": "integer", "minimum": 0},
                        "clean": {"type": "boolean"}
                      }
                    },
                    "explanation": {"type": "string"},
                    "findings": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "additionalProperties": false,
                        "required": ["category", "severity", "confidence", "matchedText", "reason"],
                        "properties": {
                          "category": {"type": "string", "enum": [
                            "PROFANITY", "HARASSMENT", "HATE_SPEECH", "THREATS", "VIOLENCE",
                            "SEXUAL_CONTENT", "BULLYING", "DEFAMATION", "SELF_HARM",
                            "ILLEGAL_ACTIVITY", "SCAM_FRAUD", "OTHER"
                          ]},
                          "severity": {"type": "string", "enum": ["CRITICAL", "HIGH", "MEDIUM", "LOW"]},
                          "confidence": {"type": "number", "minimum": 0, "maximum": 1},
                          "pageId": {"type": "string"},
                          "sectionId": {"type": "string"},
                          "elementId": {"type": "string"},
                          "elementType": {"type": "string"},
                          "matchedText": {"type": "string"},
                          "startOffset": {"type": "integer", "minimum": 0},
                          "endOffset": {"type": "integer", "minimum": 0},
                          "context": {"type": "string"},
                          "reason": {"type": "string"},
                          "suggestion": {"type": "string"}
                        }
                      }
                    }
                  }
                }
                """);
    }

    private static JsonNode read(ObjectMapper mapper, String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception exception) {
            throw new IllegalStateException("Moderation schema is invalid.", exception);
        }
    }
}
