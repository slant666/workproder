package com.example.workorder.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.workorder.auth.AuthException;
import com.example.workorder.auth.AuthService;
import com.example.workorder.auth.BootstrapAdminService;
import com.example.workorder.auth.CurrentUser;
import com.example.workorder.auth.LoginRequest;
import com.example.workorder.auth.PermissionService;
import com.example.workorder.auth.ProfileService;
import com.example.workorder.auth.RegisterRequest;
import com.example.workorder.auth.RegisterResponse;
import com.example.workorder.auth.RegistrationService;
import com.example.workorder.auth.SessionKeys;
import com.example.workorder.auth.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class AuthControllerTests {

    @Test
    void registerDelegatesWithoutCreatingLoginSession() {
        RegistrationService registrationService = org.mockito.Mockito.mock(RegistrationService.class);
        AuthController controller = new AuthController(
                registrationService,
                org.mockito.Mockito.mock(BootstrapAdminService.class),
                org.mockito.Mockito.mock(AuthService.class),
                org.mockito.Mockito.mock(ProfileService.class),
                new PermissionService());
        RegisterRequest request = new RegisterRequest("demo", "Demo User", "password123", "password123");
        RegisterResponse registered = new RegisterResponse(1L, "demo", "Demo User", "USER");
        when(registrationService.register(request)).thenReturn(registered);

        RegisterResponse response = controller.register(request);

        assertThat(response).isEqualTo(registered);
        verify(registrationService).register(request);
    }

    @Test
    void loginStoresCurrentUserInSession() {
        RegistrationService registrationService = org.mockito.Mockito.mock(RegistrationService.class);
        BootstrapAdminService bootstrapAdminService = org.mockito.Mockito.mock(BootstrapAdminService.class);
        AuthService authService = org.mockito.Mockito.mock(AuthService.class);
        ProfileService profileService = org.mockito.Mockito.mock(ProfileService.class);
        PermissionService permissionService = new PermissionService();
        AuthController controller = new AuthController(registrationService, bootstrapAdminService, authService, profileService, permissionService);
        LoginRequest request = new LoginRequest("demo", "password123");
        CurrentUser user = new CurrentUser(1L, "demo", "Demo User", "USER");
        MockHttpSession session = new MockHttpSession();
        when(authService.login(request)).thenReturn(user);

        CurrentUser response = controller.login(request, session);

        assertThat(response).isEqualTo(user);
        assertThat(session.getAttribute(SessionKeys.CURRENT_USER)).isEqualTo(user);
    }

    @Test
    void failedLoginDoesNotLeaveCurrentUserInSession() {
        AuthService authService = org.mockito.Mockito.mock(AuthService.class);
        AuthController controller = new AuthController(
                org.mockito.Mockito.mock(RegistrationService.class),
                org.mockito.Mockito.mock(BootstrapAdminService.class),
                authService,
                org.mockito.Mockito.mock(ProfileService.class),
                new PermissionService());
        LoginRequest request = new LoginRequest("demo", "wrong-password");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.CURRENT_USER, new CurrentUser(99L, "stale", "Stale", "USER"));
        when(authService.login(request)).thenThrow(new AuthException("用户名或密码错误"));

        assertThatThrownBy(() -> controller.login(request, session))
                .isInstanceOf(AuthException.class)
                .hasMessage("用户名或密码错误");
        assertThat(session.getAttribute(SessionKeys.CURRENT_USER))
                .isEqualTo(new CurrentUser(99L, "stale", "Stale", "USER"));
    }

    @Test
    void meRequiresLoggedInSession() {
        AuthController controller = new AuthController(
                org.mockito.Mockito.mock(RegistrationService.class),
                org.mockito.Mockito.mock(BootstrapAdminService.class),
                org.mockito.Mockito.mock(AuthService.class),
                org.mockito.Mockito.mock(ProfileService.class),
                new PermissionService());

        assertThatThrownBy(() -> controller.me(new MockHttpSession()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("请先登录");
    }

    @Test
    void meReturnsCurrentSessionUser() {
        AuthController controller = new AuthController(
                org.mockito.Mockito.mock(RegistrationService.class),
                org.mockito.Mockito.mock(BootstrapAdminService.class),
                org.mockito.Mockito.mock(AuthService.class),
                org.mockito.Mockito.mock(ProfileService.class),
                new PermissionService());
        CurrentUser user = new CurrentUser(1L, "demo", "Demo User", "USER");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.CURRENT_USER, user);

        assertThat(controller.me(session)).isEqualTo(user);
    }

    @Test
    void logoutInvalidatesSession() {
        AuthController controller = new AuthController(
                org.mockito.Mockito.mock(RegistrationService.class),
                org.mockito.Mockito.mock(BootstrapAdminService.class),
                org.mockito.Mockito.mock(AuthService.class),
                org.mockito.Mockito.mock(ProfileService.class),
                new PermissionService());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.CURRENT_USER, new CurrentUser(1L, "demo", "Demo User", "USER"));

        controller.logout(session);

        assertThat(session.isInvalid()).isTrue();
    }
}
