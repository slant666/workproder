package com.example.workorder.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 4, max = 30, message = "用户名长度必须为 4～30 个字符")
        String username,

        @NotBlank(message = "昵称不能为空")
        String nickname,

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, message = "密码长度至少 8 位")
        String password,

        @NotBlank(message = "确认密码不能为空")
        String confirmPassword) {
}