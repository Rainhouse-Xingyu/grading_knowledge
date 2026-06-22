package com.neusoft.grading.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量导入结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchImportResult {

    /** 总处理行数 */
    private int totalRows;

    /** 成功导入数 */
    private int successCount;

    /** 失败行详情 */
    @Builder.Default
    private List<FailRow> failRows = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailRow {
        /** Excel 行号（从 2 开始，第 1 行为表头） */
        private int rowNum;

        /** 学号 */
        private String studentNo;

        /** 失败原因 */
        private String reason;
    }
}
