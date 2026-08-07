package com.example.workorder.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.workorder.auth.AuthService;
import com.example.workorder.auth.BootstrapAdminService;
import com.example.workorder.auth.CurrentUser;
import com.example.workorder.auth.LoginRequest;
import com.example.workorder.auth.PermissionService;
import com.example.workorder.auth.ProfileService;
import com.example.workorder.auth.RegistrationService;
import com.example.workorder.auth.SessionKeys;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class AuthControllerTests {

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
