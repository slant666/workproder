package com.example.workorder.realtime;

import com.example.workorder.auth.CurrentUser;
import com.example.workorder.auth.SessionKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class RealtimeSessionHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final Map<Long, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    public RealtimeSessionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        CurrentUser user = currentUser(session);
        if (user == null) {
            closeQuietly(session, CloseStatus.NOT_ACCEPTABLE.withReason("UNAUTHORIZED"));
            return;
        }
        sessionsByUser.computeIfAbsent(user.id(), ignored -> ConcurrentHashMap.newKeySet()).add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        CurrentUser user = currentUser(session);
        if (user != null) {
            Set<WebSocketSession> sessions = sessionsByUser.get(user.id());
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    sessionsByUser.remove(user.id());
                }
            }
        }
    }

    public void sendToUser(Long userId, RealtimeEvent event) {
        if (userId == null) {
            return;
        }
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        send(sessions, event);
    }

    public void broadcast(RealtimeEvent event) {
        sessionsByUser.values().forEach(sessions -> send(sessions, event));
    }

    private void send(Set<WebSocketSession> sessions, RealtimeEvent event) {
        String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (IOException ex) {
            return;
        }
        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(message);
                } catch (IOException ex) {
                    closeQuietly(session, CloseStatus.SERVER_ERROR);
                }
            }
        }
    }

    private CurrentUser currentUser(WebSocketSession session) {
        Object value = session.getAttributes().get(SessionKeys.CURRENT_USER);
        return value instanceof CurrentUser user ? user : null;
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException ignored) {
        }
    }
}
