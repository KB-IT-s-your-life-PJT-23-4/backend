package com.example.project.common.api;

import com.example.project.common.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaginationTest {

    @Test
    @DisplayName("전체 건수와 현재 조회 건수로 페이지 메타데이터를 생성한다")
    void createPagination() {
        Pagination pagination = Pagination.of(1, 20, 45L, 20);

        assertEquals(1, pagination.getPage());
        assertEquals(20, pagination.getSize());
        assertEquals(45L, pagination.getTotalElements());
        assertEquals(3, pagination.getTotalPages());
        assertEquals(20, pagination.getNumberOfElements());
        assertFalse(pagination.isFirst());
        assertFalse(pagination.isLast());
        assertTrue(pagination.isHasNext());
        assertTrue(pagination.isHasPrevious());
    }

    @Test
    @DisplayName("페이지 번호와 크기로 조회 시작 위치를 계산한다")
    void calculateOffset() {
        assertEquals(40L, Pagination.calculateOffset(2, 20));
    }

    @Test
    @DisplayName("전체 건수를 기준으로 올림 처리한 전체 페이지 수를 계산한다")
    void calculateTotalPages() {
        assertEquals(0, Pagination.calculateTotalPages(0L, 20));
        assertEquals(1, Pagination.calculateTotalPages(20L, 20));
        assertEquals(2, Pagination.calculateTotalPages(21L, 20));
    }

    @Test
    @DisplayName("페이지 범위를 벗어나면 잘못된 요청 예외를 발생시킨다")
    void rejectInvalidPagination() {
        ServiceException negativePage = assertThrows(
                ServiceException.class,
                () -> Pagination.validate(-1, 20)
        );
        ServiceException zeroSize = assertThrows(
                ServiceException.class,
                () -> Pagination.validate(0, 0)
        );
        ServiceException excessiveSize = assertThrows(
                ServiceException.class,
                () -> Pagination.validate(0, Pagination.MAX_PAGE_SIZE + 1)
        );

        assertEquals(ResponseCode.BAD_REQUEST, negativePage.getResponseCode());
        assertEquals(ResponseCode.BAD_REQUEST, zeroSize.getResponseCode());
        assertEquals(ResponseCode.BAD_REQUEST, excessiveSize.getResponseCode());
    }
}
