package com.example.workorder.auth;

public class LoginRateLimitException extends AuthException {

    public LoginRateLimitException() {
        super("\u767b\u5f55\u5931\u8d25\u6b21\u6570\u8fc7\u591a\uff0c\u8bf7\u7a0d\u540e\u518d\u8bd5");
    }
}
