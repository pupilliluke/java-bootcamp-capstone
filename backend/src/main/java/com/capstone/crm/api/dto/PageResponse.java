package com.capstone.crm.api.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * One page of results, in the shape the UI actually reads.
 *
 * <p>Spring's {@code Page} is deliberately not returned straight from the
 * controller. Its JSON is not a published contract: it carries the whole
 * {@code Pageable} and {@code Sort} back out, and Boot 3 warns that serializing
 * it directly is unstable across versions. Naming the four fields the client
 * needs keeps the response small and stops an internal type leaking into the
 * API the way {@code InteractionEvent} once did.
 *
 * @param content       the rows on this page
 * @param page          zero-based page number, echoed back after clamping
 * @param size          page size, echoed back after clamping
 * @param totalElements how many rows match the filter, across every page
 * @param totalPages     how many pages that works out to
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    /**
     * Maps a repository page into this shape.
     *
     * <p>The mapper runs over the page's own content rather than the caller
     * building the list, so the numbers and the rows cannot disagree.
     */
    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
