package com.example.workorder.workorder;

public record CreateWorkOrderRequest(String title, String description, String type, String priority) {
}
