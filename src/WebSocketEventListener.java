package com.mostak.chatroom.controller;

import com.mostak.chatroom.model.ChatMessage;
import com.mostak.chatroom.service.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.LocalDateTime;

/**
 * WebSocketEventListener — Reacts to WebSocket connection lifecycle events.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHY DO WE NEED THIS?
 *
 * When a user closes their browser tab or loses their internet connection,
 * there is NO JavaScript that runs. No "leave" button is clicked.
 * The WebSocket connection just... dies.
 *
 * How does the server know? Via a TCP-level disconnect or heartbeat timeout.
 * Spring detects this and fires a SessionDisconnectEvent.
 *
 * Without this listener:
 *   - User closes their tab
 *   - Other users never see a "Bob left" message
 *   - Bob stays in the online users list forever
 *
 * With this listener:
 *   - Spring detects the dead connection
 *   - This method fires automatically
 *   - We broadcast a LEAVE message to everyone
 *   - Other clients remove Bob from their user lists
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * @Component
 *   Registers this as a Spring-managed bean.
 *   Generic stereotype — use when @Controller, @Service, @Repository
 *   don't quite fit. This is infrastructure/event handling code,
 *   so @Component is the right choice.
 *
 * @RequiredArgsConstructor
 *   Constructor injection for final fields (same as ChatController).
 */
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMessageRepository messageRepository;

    /**
     * handleWebSocketDisconnectListener()
     *
     * Fires automatically when ANY WebSocket connection closes.
     * This includes:
     *   - User closed browser tab
     *   - User navigated away from the page
     *   - Network connection dropped
     *   - User clicked our "Disconnect" button (which calls socket.close())
     *   - Server-side heartbeat timeout (no response from client)
     *
     * @EventListener
     *   Registers this method as a listener for SessionDisconnectEvent.
     *   Spring's event bus calls this method whenever that event is published.
     *   No manual wiring needed — @EventListener does it automatically.
     *
     * @param event
     *   Contains information about the closed session:
     *     - The session ID
     *     - The STOMP message (with session attributes we stored earlier)
     *     - Whether it was a clean close
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {

        /**
         * StompHeaderAccessor.wrap(event.getMessage())
         *
         * Wraps the raw Spring Message into a STOMP-aware accessor.
         * This gives us access to STOMP headers and — importantly —
         * the session attributes Map we populated in ChatController.addUser().
         *
         * In addUser() we did:
         *   headerAccessor.getSessionAttributes().put("username", sender)
         *
         * Here we retrieve that same value:
         *   headerAccessor.getSessionAttributes().get("username")
         *
         * This is how we know WHICH user disconnected.
         */
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String username = (String) headerAccessor.getSessionAttributes().get("username");

        /**
         * Guard: username can be null if:
         *   - The user connected but never sent /app/chat.addUser
         *     (e.g., they connected and immediately disconnected)
         *   - Session attributes weren't initialized properly
         *
         * If null, there's nothing to broadcast — exit early.
         */
        if (username == null) {
            return;
        }

        // Build the LEAVE notification message
        ChatMessage leaveMessage = ChatMessage.builder()
            .type(ChatMessage.MessageType.LEAVE)
            .sender(username)
            .content(username + " left the chat")
            .sentAt(LocalDateTime.now())
            .build();

        // Persist the LEAVE event
        messageRepository.save(leaveMessage);

        /**
         * Broadcast to all remaining connected clients.
         *
         * We CANNOT use @SendTo here because this is not a @MessageMapping method.
         * @SendTo only works on the return value of @MessageMapping handlers.
         * Here we're in an event listener, so we use messagingTemplate directly.
         *
         * convertAndSend(destination, payload):
         *   1. Jackson serializes leaveMessage → JSON string
         *   2. Spring sends it to the in-memory broker at /topic/chatroom
         *   3. Broker fans out to ALL current subscribers
         *   4. Each client receives the JSON and removes the user from their list
         */
        messagingTemplate.convertAndSend("/topic/chatroom", leaveMessage);
    }
}
