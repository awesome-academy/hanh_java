package com.psms.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Kết quả import CSV.
 * Không fail toàn bộ nếu 1 row lỗi — collect từng lỗi, tiếp tục xử lý.
 *
 * <p>Format: {@code { total, success, failed, errors: [{row, field, message}] }}
 */
@Getter
@Builder
public class ImportResult {

    private int total;
    private int success;
    private int failed;
    private List<RowError> errors;

    /** Thông tin lỗi của từng row */
    @Getter
    @Builder
    public static class RowError {
        /** Số thứ tự row trong file CSV (1-based, không tính header) */
        private int row;
        /** Field bị lỗi (tên cột CSV) */
        private String field;
        /** Mô tả lỗi */
        private String message;
    }
}

