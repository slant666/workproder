package com.example.workorder.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.workorder.auth.AuthException;
import com.example.workorder.auth.AuthService;
import com.example.workorder.auth.BootstrapAdminService;
import com.example.workorder.auth.CurrentUser;
import com.example.workorder.auth.CsrfTokenService;
import com.example.workorder.auth.EmailVerificationService;
import com.example.workorder.auth.LoginRequest;
import com.example.workorder.auth.PasswordResetService;
import com.example.workorder.auth.PermissionService;
import com.example.workorder.auth.ProfileService;
import com.example.workorder.auth.RegisterRequest;
import com.example.workorder.auth.RegisterResponse;
import com.example.workorder.auth.RegistrationService;
import com.example.workorder.auth.SessionKeys;
import com.example.workorder.auth.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

class AuthControllerTests {

    private final CsrfTokenService csrfTokenService = new CsrfTokenService();

    @Test
    void registerDelegatesWithoutCreatingLoginSession() {
        RegistrationService registrationService = org.mockito.Mockito.mock(RegistrationService.class);
        AuthController controller = newController(registrationService, org.mockito.Mockito.mock(AuthService.class));
        RegisterRequest request = new RegisterRequest("demo", "Demo User", "password123", "password123");
        RegisterResponse registered = new RegisterResponse(1L, "demo", "Demo User", "USER");
        when(registrationService.register(request)).thenReturn(registered);

        RegisterResponse response = controller.register(request);

        assertThat(response).isEqualTo(registered);
        verify(registrationService).register(request);
    }

    @Test
    void loginStoresCurrentUserInSession() {
        AuthService authService = org.mockito.Mockito.mock(AuthService.class);
        AuthController controller = newController(org.mockito.Mockito.mock(RegistrationService.class), authService);
        LoginRequest request = new LoginRequest("demo", "password123");
        CurrentUser user = new CurrentUser(1L, "demo", "Demo User", "USER");
        MockHttpSession session = new MockHttpSession();
        when(authService.login(request)).thenReturn(user);

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setSession(session);

        CurrentUser response = controller.login(request, httpRequest, session);

        assertThat(response).isEqualTo(user);
        assertThat(session.getAttribute(SessionKeys.CURRENT_USER)).isEqualTo(user);
    }

    @Test
    void loginRotatesCsrfTokenAfterAuthentication() {
        AuthService authService = org.mockito.Mockito.mock(AuthService.class);
        AuthController controller = newController(org.mockito.Mockito.mock(RegistrationService.class), authService);
        LoginRequest request = new LoginRequest("demo", "password123");
        CurrentUser user = new CurrentUser(1L, "demo", "Demo User", "USER");
        MockHttpSession session = new MockHttpSession();
        String beforeLogin = csrfTokenService.getOrCreateToken(session);
        when(authService.login(request)).thenReturn(user);

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setSession(session);

        controller.login(request, httpRequest, session);

        assertThat(csrfTokenService.getOrCreateToken(session)).isNotEqualTo(beforeLogin);
    }

    @Test
    void csrfReturnsReusableSessionToken() {
        AuthController controller = newController(
                org.mockito.Mockito.mock(RegistrationService.class),
                org.mockito.Mockito.mock(AuthService.class));
        MockHttpSession session = new MockHttpSession();

        String first = controller.csrf(session).token();
        String second = controller.csrf(session).token();

        assertThat(first).isNotBlank();
        assertThat(second).isEqualTo(first);
    }

    @Test
    void failedLoginDoesNotLeaveCurrentUserInSession() {
        AuthService authService = org.mockito.Mockito.mock(AuthService.class);
        AuthController controller = newController(org.mockito.Mockito.mock(RegistrationService.class), authService);
        LoginRequest request = new LoginRequest("demo", "wrong-password");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.CURRENT_USER, new CurrentUser(99L, "stale", "Stale", "USER"));
        when(authService.login(request)).thenThrow(new AuthException("用户名或密码错误"));

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setSession(session);

        assertThatThrownBy(() -> controller.login(request, httpRequest, session))
                .isInstanceOf(AuthException.class)
                .hasMessage("用户名或密码错误");
        assertThat(session.getAttribute(SessionKeys.CURRENT_USER))
                .isEqualTo(new CurrentUser(99L, "stale", "Stale", "USER"));
    }

    @Test
    void meRequiresLoggedInSession() {
        AuthController controller = newController(
                org.mockito.Mockito.mock(RegistrationService.class),
                org.mockito.Mockito.mock(AuthService.class));

        assertThatThrownBy(() -> controller.me(new MockHttpSession()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("请先登录");
    }

    @Test
    void meReturnsCurrentSessionUser() {
        AuthController controller = newController(
                org.mockito.Mockito.mock(RegistrationService.class),
                org.mockito.Mockito.mock(AuthService.class));
        CurrentUser user = new CurrentUser(1L, "demo", "Demo User", "USER");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.CURRENT_USER, user);

        assertThat(controller.me(session)).isEqualTo(user);
    }

    @Test
    void logoutInvalidatesSession() {
        AuthController controller = newController(
                org.mockito.Mockito.mock(RegistrationService.class),
                org.mockito.Mockito.mock(AuthService.class));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.CURRENT_USER, new CurrentUser(1L, "demo", "Demo User", "USER"));

        controller.logout(session);

        assertThat(session.isInvalid()).isTrue();
    }

    private AuthController newController(RegistrationService registrationService, AuthService authService) {
        return new AuthController(
                registrationService,
                org.mockito.Mockito.mock(BootstrapAdminService.class),
                authService,
                org.mockito.Mockito.mock(ProfileService.class),
                new PermissionService(),
                csrfTokenService,
                org.mockito.Mockito.mock(PasswordResetService.class),
                org.mockito.Mockito.mock(EmailVerificationService.class));
    }
}
