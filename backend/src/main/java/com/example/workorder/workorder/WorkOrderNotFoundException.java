package com.example.workorder.workorder;

public class WorkOrderNotFoundException extends RuntimeException {
    public WorkOrderNotFoundException() {
        super("工单不存在");
    }
}