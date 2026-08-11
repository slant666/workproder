package com.example.workorder.auth;

import jakarta.servlet.http.HttpSession;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Service;

@Service
public class CsrfTokenService {

    public static final String HEADER_NAME = "X-CSRF-Token";
    private static final String SESSION_ATTRIBUTE = "CSRF_TOKEN";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public String getOrCreateToken(HttpSession session) {
        Object existing = session.getAttribute(SESSION_ATTRIBUTE);
        if (existing instanceof String token && !token.isBlank()) {
            return token;
        }
        String token = generateToken();
        session.setAttribute(SESSION_ATTRIBUTE, token);
        return token;
    }

    public void rotateToken(HttpSession session) {
        session.setAttribute(SESSION_ATTRIBUTE, generateToken());
    }

    public boolean matches(HttpSession session, String providedToken) {
        if (session == null) {
            return false;
        }
        Object expected = session.getAttribute(SESSION_ATTRIBUTE);
        return expected instanceof String token && token.equals(providedToken);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
