package com.example.workorder.realtime;

import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class RealtimeEventPublisher {

    private final RealtimeSessionHandler sessionHandler;

    public RealtimeEventPublisher(RealtimeSessionHandler sessionHandler) {
        this.sessionHandler = sessionHandler;
    }

    public void notifyUser(Long userId, String type, Long entityId, Long notificationId, Long unreadCount, Map<String, Object> payload) {
        RealtimeEvent event = RealtimeEvent.of(type, entityId, notificationId, unreadCount, payload);
        afterCommit(() -> sessionHandler.sendToUser(userId, event));
    }

    public void broadcast(String type, Long entityId, Map<String, Object> payload) {
        RealtimeEvent event = RealtimeEvent.of(type, entityId, null, null, payload);
        afterCommit(() -> sessionHandler.broadcast(event));
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }
}
