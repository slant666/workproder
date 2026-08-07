package com.example.workorder.workorder;

public record WorkOrderListQuery(
        String keyword,
        String status,
        String priority,
        Long creatorId,
        Long handlerId,
        String createdFrom,
        String createdTo,
        String sort,
        Integer page,
        Integer pageSize) {

    public WorkOrderListQuery(String keyword, String status, String priority, String sort, Integer page, Integer pageSize) {
        this(keyword, status, priority, null, null, null, null, sort, page, pageSize);
    }
}
