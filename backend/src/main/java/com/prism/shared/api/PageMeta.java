package com.prism.shared.api;

public record PageMeta(int page, int size, long total, int totalPages) {

    public static PageMeta of(org.springframework.data.domain.Page<?> page) {
        return new PageMeta(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
