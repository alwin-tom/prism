package com.prism.shared.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, List<FieldError> fields) {

    public ApiError(String code, String message) {
        this(code, message, null);
    }

    public record FieldError(String field, String message) {
    }
}
