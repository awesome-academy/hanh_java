package com.psms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO cho dashboard chart data — phân bố hồ sơ theo lĩnh vực và trạng thái.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardChartResponse {

    /** Tên nhãn (tên lĩnh vực hoặc label trạng thái). */
    private String label;

    /** Số lượng hồ sơ. */
    private long count;

    /** Phần trăm so với tổng (0-100), dùng cho CSS width%. */
    private int percent;

    /** CSS class bổ sung (màu badge cho status chart). Null cho category chart. */
    private String cssClass;

    /**
     * Tính percent cho từng item dựa trên tổng count.
     * Min 1% để thanh bar luôn hiển thị dù count nhỏ.
     */
    public static List<DashboardChartResponse> withPercent(List<DashboardChartResponse> items) {
        long total = items.stream().mapToLong(DashboardChartResponse::getCount).sum();
        if (total == 0) return items;
        items.forEach(item -> {
            int pct = (int) Math.round(item.getCount() * 100.0 / total);
            item.setPercent(Math.max(pct, 1));
        });
        return items;
    }
}

