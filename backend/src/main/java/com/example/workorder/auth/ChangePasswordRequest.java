package com.example.workorder.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "原密码不能为空")
        String currentPassword,

        @NotBlank(message = "新密码不能为空")
        @Size(min = 8, message = "新密码长度至少 8 位")
        String newPassword,

        @NotBlank(message = "确认密码不能为空")
        String confirmPassword) {
}