package com.allianz.pac.baseservice.dto.response;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Stable pagination payload (returned in {@code data}); avoids exposing Spring's {@code Page} JSON.
 *
 * @param content       items of the current page
 * @param page          zero-based page number
 * @param size          requested page size
 * @param totalElements total number of items
 * @param totalPages    total number of pages
 * @param last          whether this is the last page
 * @param <T>           item type
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages, boolean last) {

    /**
     * Makes the content list immutable.
     */
    public PageResponse {
        content = content == null ? List.of() : List.copyOf(content);
    }

    /**
     * Converts a Spring Data page into a page response.
     *
     * @param page Spring Data page
     * @param <T>  item type
     * @return the page response
     */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }
}
