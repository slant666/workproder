package com.example.workorder.auth;

public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException() {
        super("请先登录");
    }
}