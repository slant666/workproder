package com.example.workorder.workorder;

import java.util.List;

public record WorkOrderStatisticsResponse(
        long total,
        List<WorkOrderCountResponse> statusCounts,
        List<WorkOrderCountResponse> priorityCounts,
        List<DailyWorkOrderCountResponse> dailyNewCounts,
        long averageProcessingMinutes,
        List<AdminWorkOrderCountResponse> adminProcessingCounts,
        long overdueUnhandledCount,
        String averageProcessingRule,
        String overdueRule) {
}