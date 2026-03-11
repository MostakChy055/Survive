package com.chatroom.controller;

import com.chatroom.model.ChatMessage;
import com.chatroom.service.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.LocalDateTime;

/**
 * WebSocketEventListener — Listens to WebSocket lifecycle events.
 *
 * Spring's event system (ApplicationEvent + @EventListener) lets us react
 * to things that happen in the application without tight coupling.
 *
 * We care about SessionDisconnectEvent — fired when a WebSocket connection closes.
 * This could happen because:
 *   - The user clicked "Leave" or logged out
 *   - The browser was closed
 *   - Network connection was lost
 *   - Server timed out the connection
 *
 * When a disconnect happens, we want to:
 *   1. Find out WHO disconnected (from the session attributes we saved in addUser())
 *   2. Broadcast a LEAVE notification to all remaining users
 *   3. Save the LEAVE event to the database
 *
 * @Component — Registers this as a Spring-managed bean.
 *   Unlike @Controller (for web/WebSocket handlers) or @Service (business logic),
 *   @Component is a generic stereotype for any Spring-managed class.
 *
 * @RequiredArgsConstructor — Generates constructor injection for final fields.
 */
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMessageRepository messageRepository;

    /**
     * handleWebSocketDisconnectListener() — Reacts to user disconnection.
     *
     * @EventListener — Tells Spring to call this method whenever a
     *   SessionDisconnectEvent is published in the application context.
     *   Spring's event bus calls this automatically — no manual wiring needed.
     *
     * @param event — Contains information about the disconnected session,
     *   including the session attributes we stored in ChatController.addUser().
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {

        /**
         * Retrieve the WebSocket session's attribute map.
         * In ChatController.addUser(), we stored:
         *   headerAccessor.getSessionAttributes().put("username", principal.getName())
         *
         * Here we retrieve that same value.
         * StompHeaderAccessor.wrap(event.getMessage()) gives us access
         * to the session attributes of the disconnected session.
         */
        var headerAccessor = org.springframework.messaging.simp.stomp.StompHeaderAccessor
            .wrap(event.getMessage());

        String username = (String) headerAccessor.getSessionAttributes().get("username");

        /**
         * Guard against null username.
         * This can happen if:
         *   - The user connected but never sent /app/chat.addUser
         *   - A non-STOMP WebSocket connection disconnected
         *   - The session attributes were lost
         *
         * In those cases, we have nothing to broadcast, so we exit early.
         */
        if (username == null) {
            return;
        }

        // Create the LEAVE notification message
        ChatMessage leaveMessage = ChatMessage.builder()
            .type(ChatMessage.MessageType.LEAVE)
            .sender(username)
            .content(username + " left the chat")
            .sentAt(LocalDateTime.now())
            .build();

        // Persist the LEAVE event to the database
        messageRepository.save(leaveMessage);

        /**
         * Broadcast the LEAVE message to all remaining connected clients.
         *
         * We use messagingTemplate.convertAndSend() instead of @SendTo
         * because this method is not a @MessageMapping handler —
         * it's an event listener. @SendTo only works on @MessageMapping methods.
         *
         * convertAndSend(destination, payload):
         *   - Serializes leaveMessage to JSON using Jackson
         *   - Sends the JSON to /topic/chatroom
         *   - The in-memory broker delivers it to all current subscribers
         */
        messagingTemplate.convertAndSend("/topic/chatroom", leaveMessage);
    }
}
