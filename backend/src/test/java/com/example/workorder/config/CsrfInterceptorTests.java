package com.example.workorder.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.workorder.auth.CsrfException;
import com.example.workorder.auth.CsrfTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

class CsrfInterceptorTests {

    private final CsrfTokenService csrfTokenService = new CsrfTokenService();
    private final CsrfInterceptor interceptor = new CsrfInterceptor(csrfTokenService);

    @Test
    void safeMethodsDoNotRequireToken() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/work-orders");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
    }

    @Test
    void unsafeMethodsRejectMissingToken() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/work-orders");

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(CsrfException.class)
                .hasMessage("CSRF token is missing or invalid");
    }

    @Test
    void unsafeMethodsAcceptMatchingSessionToken() {
        MockHttpSession session = new MockHttpSession();
        String token = csrfTokenService.getOrCreateToken(session);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/work-orders");
        request.setSession(session);
        request.addHeader(CsrfTokenService.HEADER_NAME, token);

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
    }
}
