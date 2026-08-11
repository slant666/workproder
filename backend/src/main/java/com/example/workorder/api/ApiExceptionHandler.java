package com.example.workorder.api;

import com.example.workorder.auth.AdminUserException;
import com.example.workorder.auth.AuthException;
import com.example.workorder.auth.CsrfException;
import com.example.workorder.auth.ForbiddenException;
import com.example.workorder.auth.RegistrationException;
import com.example.workorder.auth.UnauthorizedException;
import com.example.workorder.workorder.WorkOrderException;
import com.example.workorder.workorder.WorkOrderNotFoundException;
import com.example.workorder.workorder.WorkOrderStateException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> validation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "Invalid request" : error.getDefaultMessage())
                .orElse("Invalid request");
        return Map.of("message", message);
    }

    @ExceptionHandler(RegistrationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> registration(RegistrationException ex) {
        return Map.of("message", ex.getMessage());
    }

    @ExceptionHandler(AuthException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> auth(AuthException ex) {
        return Map.of("message", ex.getMessage());
    }

    @ExceptionHandler(AdminUserException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> adminUser(AdminUserException ex) {
        return Map.of("message", ex.getMessage());
    }

    @ExceptionHandler(WorkOrderException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> workOrder(WorkOrderException ex) {
        return Map.of("message", ex.getMessage());
    }

    @ExceptionHandler(WorkOrderNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> workOrderNotFound(WorkOrderNotFoundException ex) {
        return Map.of("message", ex.getMessage());
    }

    @ExceptionHandler(WorkOrderStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> workOrderState(WorkOrderStateException ex) {
        return Map.of("message", ex.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> unauthorized(UnauthorizedException ex) {
        return Map.of("message", ex.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, String> forbidden(ForbiddenException ex) {
        return Map.of("message", ex.getMessage());
    }

    @ExceptionHandler(CsrfException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, String> csrf(CsrfException ex) {
        return Map.of("message", ex.getMessage());
    }
}
