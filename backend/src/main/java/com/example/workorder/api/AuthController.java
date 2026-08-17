package com.example.workorder.api;

import com.example.workorder.auth.AuthService;
import com.example.workorder.auth.BootstrapAdminRequest;
import com.example.workorder.auth.BootstrapAdminService;
import com.example.workorder.auth.ChangePasswordRequest;
import com.example.workorder.auth.CurrentUser;
import com.example.workorder.auth.CsrfTokenResponse;
import com.example.workorder.auth.CsrfTokenService;
import com.example.workorder.auth.EmailVerificationRequest;
import com.example.workorder.auth.EmailVerificationService;
import com.example.workorder.auth.LoginRequest;
import com.example.workorder.auth.PasswordResetRequest;
import com.example.workorder.auth.PasswordResetConfirmRequest;
import com.example.workorder.auth.PasswordResetService;
import com.example.workorder.auth.PermissionService;
import com.example.workorder.auth.ProfileService;
import com.example.workorder.auth.RegisterRequest;
import com.example.workorder.auth.RegisterResponse;
import com.example.workorder.auth.RegistrationService;
import com.example.workorder.auth.SessionKeys;
import com.example.workorder.auth.UpdateProfileRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final BootstrapAdminService bootstrapAdminService;
    private final AuthService authService;
    private final ProfileService profileService;
    private final PermissionService permissionService;
    private final CsrfTokenService csrfTokenService;
    private final PasswordResetService passwordResetService;
    private final EmailVerificationService emailVerificationService;

    public AuthController(
            RegistrationService registrationService,
            BootstrapAdminService bootstrapAdminService,
            AuthService authService,
            ProfileService profileService,
            PermissionService permissionService,
            CsrfTokenService csrfTokenService,
            PasswordResetService passwordResetService,
            EmailVerificationService emailVerificationService) {
        this.registrationService = registrationService;
        this.bootstrapAdminService = bootstrapAdminService;
        this.authService = authService;
        this.profileService = profileService;
        this.permissionService = permissionService;
        this.csrfTokenService = csrfTokenService;
        this.passwordResetService = passwordResetService;
        this.emailVerificationService = emailVerificationService;
    }

    @GetMapping("/csrf")
    public CsrfTokenResponse csrf(HttpSession session) {
        return new CsrfTokenResponse(csrfTokenService.getOrCreateToken(session));
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return registrationService.register(request);
    }

    @PostMapping("/password-reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.requestReset(request);
    }

    @PostMapping("/email/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody EmailVerificationRequest request) {
        emailVerificationService.verify(request);
    }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirmReset(request);
    }

    @PostMapping("/bootstrap-admin")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse bootstrapAdmin(
            @Valid @RequestBody BootstrapAdminRequest request,
            @RequestHeader(value = "X-Bootstrap-Token", required = false) String bootstrapToken) {
        return bootstrapAdminService.createFirstAdmin(request, bootstrapToken);
    }

    @PostMapping("/login")
    public CurrentUser login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpSession session) {
        CurrentUser currentUser = authService.login(request);
        httpRequest.changeSessionId();
        csrfTokenService.rotateToken(session);
        session.setAttribute(SessionKeys.CURRENT_USER, currentUser);
        return currentUser;
    }

    @GetMapping("/me")
    public CurrentUser me(HttpSession session) {
        return permissionService.requireUser(session);
    }

    @PatchMapping("/profile")
    public CurrentUser updateProfile(@Valid @RequestBody UpdateProfileRequest request, HttpSession session) {
        CurrentUser updated = profileService.updateProfile(permissionService.requireUser(session), request);
        session.setAttribute(SessionKeys.CURRENT_USER, updated);
        return updated;
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request, HttpSession session) {
        profileService.changePassword(permissionService.requireUser(session), request);
        session.invalidate();
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpSession session) {
        session.invalidate();
    }
}
