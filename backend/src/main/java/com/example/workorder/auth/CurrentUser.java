package com.example.workorder.auth;

public record CurrentUser(Long id, String username, String nickname, String role) {
}