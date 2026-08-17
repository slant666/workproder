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
        long slaNearOverdueCount,
        long firstResponseOverdueCount,
        long resolutionOverdueCount,
        List<WorkOrderCountResponse> slaOverduePriorityCounts,
        String averageProcessingRule,
        String overdueRule) {

    public WorkOrderStatisticsResponse(
            long total,
            List<WorkOrderCountResponse> statusCounts,
            List<WorkOrderCountResponse> priorityCounts,
            List<DailyWorkOrderCountResponse> dailyNewCounts,
            long averageProcessingMinutes,
            List<AdminWorkOrderCountResponse> adminProcessingCounts,
            long overdueUnhandledCount,
            String averageProcessingRule,
            String overdueRule) {
        this(total, statusCounts, priorityCounts, dailyNewCounts, averageProcessingMinutes, adminProcessingCounts,
                overdueUnhandledCount, 0, 0, 0, List.of(), averageProcessingRule, overdueRule);
    }
}
