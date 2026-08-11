package com.example.workorder.config;

import com.example.workorder.auth.CsrfException;
import com.example.workorder.auth.CsrfTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class CsrfInterceptor implements HandlerInterceptor {

    private final CsrfTokenService csrfTokenService;

    public CsrfInterceptor(CsrfTokenService csrfTokenService) {
        this.csrfTokenService = csrfTokenService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (isSafeMethod(request.getMethod())) {
            return true;
        }
        String token = request.getHeader(CsrfTokenService.HEADER_NAME);
        if (!csrfTokenService.matches(request.getSession(false), token)) {
            throw new CsrfException();
        }
        return true;
    }

    private boolean isSafeMethod(String method) {
        return "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method);
    }
}
