package com.example.workorder.auth;

public record RegisterResponse(
        Long id,
        String username,
        String nickname,
        String role,
        Long companyId,
        String companyName,
        Long departmentId,
        String departmentName,
        Long teamId,
        String teamName,
        boolean orgConfirmed) {

    public RegisterResponse(Long id, String username, String nickname, String role) {
        this(id, username, nickname, role, null, null, null, null, null, null, false);
    }
}
