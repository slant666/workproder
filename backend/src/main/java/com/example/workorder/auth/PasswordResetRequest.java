package com.example.workorder.auth;

import jakarta.validation.constraints.NotBlank;

public record PasswordResetRequest(
        @NotBlank(message = "Username or email is required")
        String usernameOrEmail) {
}
