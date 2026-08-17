package com.example.workorder.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Username is required")
        @Size(min = 4, max = 30, message = "Username must be 4 to 30 characters")
        String username,

        @NotBlank(message = "Nickname is required")
        String nickname,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password,

        @NotBlank(message = "Password confirmation is required")
        String confirmPassword,

        @Email(message = "Email format is invalid")
        String email,

        Long companyId,
        Long departmentId,
        Long teamId) {

    public RegisterRequest(String username, String nickname, String password, String confirmPassword) {
        this(username, nickname, password, confirmPassword, null, null, null, null);
    }
}
