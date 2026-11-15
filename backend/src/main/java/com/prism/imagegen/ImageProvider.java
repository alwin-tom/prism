package com.prism.imagegen;

import java.math.BigDecimal;

public interface ImageProvider {

    String id();

    GeneratedImage generate(Request request);

    record Request(
            String model,
            String prompt,
            String aspectRatio,
            String outputFormat,
            boolean includeAspectRatio,
            boolean includeOutputFormat,
            boolean includeN
    ) {
        public Request(String model, String prompt, String aspectRatio, String outputFormat) {
            this(model, prompt, aspectRatio, outputFormat, true, true, true);
        }
    }

    record GeneratedImage(byte[] bytes, String mimeHint, String model, BigDecimal costUsd, int latencyMs) {
    }
}
