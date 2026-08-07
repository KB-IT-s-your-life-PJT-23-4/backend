package com.example.project.common.api;

import com.example.project.common.exception.ServiceException;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class Pagination {

    public static final int MAX_PAGE_SIZE = 100;

    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
    private final int numberOfElements;
    private final boolean first;
    private final boolean last;
    private final boolean hasNext;
    private final boolean hasPrevious;

    private Pagination(
            int page,
            int size,
            long totalElements,
            int totalPages,
            int numberOfElements,
            boolean first,
            boolean last,
            boolean hasNext,
            boolean hasPrevious
    ) {
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.numberOfElements = numberOfElements;
        this.first = first;
        this.last = last;
        this.hasNext = hasNext;
        this.hasPrevious = hasPrevious;
    }

    public static Pagination of(
            int page,
            int size,
            long totalElements,
            int numberOfElements
    ) {
        validate(page, size);

        long pages = calculateTotalPageCount(totalElements, size);
        int totalPages = pages > Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int) pages;
        boolean hasNext = page + 1L < pages;

        return new Pagination(
                page,
                size,
                totalElements,
                totalPages,
                numberOfElements,
                page == 0,
                !hasNext,
                hasNext,
                page > 0
        );
    }

    public static void validate(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }
    }

    public static long calculateOffset(int page, int size) {
        validate(page, size);
        return (long) page * size;
    }

    public static int calculateTotalPages(long totalElements, int size) {
        validate(0, size);
        long pages = calculateTotalPageCount(totalElements, size);
        return pages > Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int) pages;
    }

    private static long calculateTotalPageCount(long totalElements, int size) {
        return totalElements / size
                + (totalElements % size == 0 ? 0 : 1);
    }
}
