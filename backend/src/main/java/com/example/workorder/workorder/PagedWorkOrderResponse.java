package com.example.workorder.workorder;

import java.util.List;

public record PagedWorkOrderResponse(
        List<WorkOrderResponse> items,
        long total,
        int page,
        int pageSize,
        int totalPages) {
}
