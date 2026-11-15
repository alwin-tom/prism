package com.prism.imagegen;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public record ImageModelCard(
        String id,
        String name,
        String description,
        Set<String> supportedAspectRatios,
        Set<String> supportedResolutions,
        Set<String> supportedParameters,
        BigDecimal pricePerImageUsd,
        boolean priceKnown,
        boolean free
) {
    public boolean supportsAspect(String aspect) {
        if (aspect == null || aspect.isBlank() || supportedAspectRatios == null || supportedAspectRatios.isEmpty()) {
            return true;
        }
        return supportedAspectRatios.contains(aspect);
    }

    public List<String> parameterNames() {
        return supportedParameters == null ? List.of() : List.copyOf(supportedParameters);
    }
}
