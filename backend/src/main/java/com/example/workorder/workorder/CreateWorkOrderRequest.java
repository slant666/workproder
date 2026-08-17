package com.example.workorder.workorder;

public record CreateWorkOrderRequest(String title, String description, String type, String priority, String idempotencyKey) {

    public CreateWorkOrderRequest(String title, String description, String type, String priority) {
        this(title, description, type, priority, null);
    }
}
