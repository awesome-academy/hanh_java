package com.psms.util;

/**
 * Value object chứa thông tin phân trang đã tính sẵn.
 *
 * <p>Dùng để truyền từ controller sang Thymeleaf template,
 * tránh gọi {@code T(Math)} trong SpEL expression (Thymeleaf không hỗ trợ tốt).
 *
 * <p>Là Java record — immutable, không cần getter/setter, không liên quan JPA.
 */
public record PaginationInfo(int pageStart, int pageEnd, long displayFrom, long displayTo) {
}

