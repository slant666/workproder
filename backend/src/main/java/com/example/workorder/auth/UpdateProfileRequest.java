package com.example.workorder.auth;

import jakarta.validation.constraints.NotBlank;

public record UpdateProfileRequest(
        @NotBlank(message = "昵称不能为空")
        String nickname) {
}