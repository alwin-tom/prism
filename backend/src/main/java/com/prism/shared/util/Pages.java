package com.prism.shared.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

public final class Pages {

    private Pages() {
    }

    public static Pageable of(Integer page, Integer size, String sort, String defaultProperty, Set<String> allowed) {
        int pageNumber = page == null ? 0 : Math.max(page, 0);
        int pageSize = size == null ? 20 : Math.min(Math.max(size, 1), 100);
        String property = defaultProperty;
        Sort.Direction direction = Sort.Direction.DESC;
        if (sort != null && sort.contains(",")) {
            String[] parts = sort.split(",", 2);
            if (allowed.contains(parts[0])) {
                property = parts[0];
            }
            if ("asc".equalsIgnoreCase(parts[1])) {
                direction = Sort.Direction.ASC;
            }
        }
        return PageRequest.of(pageNumber, pageSize, Sort.by(direction, property));
    }
}
