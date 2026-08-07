package com.example.workorder.workorder;

public record UpdateWorkOrderRequest(String title, String description, String type, String priority) {
}
