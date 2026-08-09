package com.example.workorder.auth;

public record AdminUserListQuery(String keyword, Integer page, Integer pageSize) {
}