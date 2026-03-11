package com.mostak.chatroom.controller;

import com.mostak.chatroom.model.ChatMessage;
import com.mostak.chatroom.service.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;

/**
 * ChatController — Handles incoming STOMP messages from WebSocket clients.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THIS IS NOT A REST CONTROLLER.
 *
 * A @RestController handles HTTP requests (GET, POST, etc.)
 * A @Controller with @MessageMapping handles STOMP messages over WebSocket.
 *
 * The difference:
 *   HTTP:      client → request → server → response → client (one-to-one)
 *   WebSocket: client → message → server → broadcast → ALL clients (one-to-many)
 *
 * @MessageMapping is to WebSocket what @RequestMapping is to HTTP.
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * @Controller
 *   Registers this class as a Spring-managed component.
 *   Spring scans for @Controller at startup and wires it in.
 *
 * @RequiredArgsConstructor (Lombok)
 *   Generates a constructor for all 'final' fields.
 *   This enables constructor injection — the recommended way to inject
 *   dependencies (better than @Autowired field injection).
 *
 *   Generated constructor:
 *     public ChatController(
 *         ChatMessageRepository messageRepository,
 *         SimpMessagingTemplate messagingTemplate
 *     ) {
 *         this.messageRepository = messageRepository;
 *         this.messagingTemplate = messagingTemplate;
 *     }
 *
 *   Spring calls this constructor automatically and provides the beans.
 */
@Controller
@RequiredArgsConstructor
public class ChatController {

    /**
     * messageRepository — Saves and loads messages from H2 database.
     * 'final' + @RequiredArgsConstructor = Spring injects this automatically.
     */
    private final ChatMessageRepository messageRepository;

    /**
     * messagingTemplate — Sends messages FROM the server to clients.
     *
     * Used when we need to send programmatically (not via @SendTo).
     * @SendTo only works on the return value of @MessageMapping methods.
     * messagingTemplate works anywhere — event listeners, scheduled tasks, etc.
     *
     * Two key methods:
     *   convertAndSend(destination, payload)
     *     → Sends to ALL subscribers of the destination
     *
     *   convertAndSendToUser(username, destination, payload)
     *     → Sends to ONE specific user's private queue
     */
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * sendMessage() — Handles a chat message sent by a user.
     *
     * ─────────────────────────────────────────────────────────────────────────
     * FULL MESSAGE FLOW for a CHAT message:
     *
     *   1. User types "Hello" in the browser
     *   2. JavaScript calls: socket.send(JSON.stringify({
     *          type: "CHAT", sender: "alice", content: "Hello"
     *      }))
     *      (In Phase 4 this becomes: stompClient.publish({
     *          destination: "/app/chat.send", body: JSON.stringify(...)
     *      }))
     *   3. STOMP frame arrives at Spring: SEND destination:/app/chat.send
     *   4. Spring strips "/app" → looks for @MessageMapping("/chat.send")
     *   5. THIS METHOD runs
     *   6. We set sentAt (server time), ensure type is CHAT
     *   7. We save to H2 database
     *   8. @SendTo broadcasts the saved message to /topic/chatroom
     *   9. In-memory broker delivers it to EVERY subscriber of /topic/chatroom
     *   10. Every connected browser receives it in their onmessage handler
     * ─────────────────────────────────────────────────────────────────────────
     *
     * @MessageMapping("/chat.send")
     *   Maps STOMP messages sent to destination "/app/chat.send" to this method.
     *   The "/app" prefix is stripped by Spring (configured in WebSocketConfig).
     *   So "/app/chat.send" matches @MessageMapping("/chat.send").
     *
     * @SendTo("/topic/chatroom")
     *   After this method returns, Spring takes the return value
     *   and automatically sends it to /topic/chatroom.
     *   The in-memory broker delivers it to all current subscribers.
     *   This is the BROADCAST — every connected user gets the message.
     *
     * @Payload ChatMessage chatMessage
     *   Spring deserializes the STOMP message body (JSON string) into
     *   a ChatMessage Java object using Jackson.
     *   The JSON { "content": "Hello", "sender": "alice", "type": "CHAT" }
     *   becomes a ChatMessage with those fields populated.
     *
     * NOTE — Phase 2 vs Phase 5 difference:
     *   In Phase 2 we trust chatMessage.getSender() from the client.
     *   In Phase 5 (Security), we replace it with Principal.getName()
     *   so the server controls the sender identity. Clients cannot fake it.
     */
    @MessageMapping("/chat.send")
    @SendTo("/topic/chatroom")
    public ChatMessage sendMessage(@Payload ChatMessage chatMessage) {

        /**
         * Set the server-side timestamp.
         *
         * We always override this with server time regardless of what
         * the client sent. This ensures consistent message ordering.
         */
        chatMessage.setSentAt(LocalDateTime.now());

        /**
         * Force the type to CHAT.
         *
         * Even if a client sends type=JOIN or type=LEAVE through this endpoint,
         * we override it. JOIN/LEAVE are handled by addUser() below.
         * Each endpoint has exactly one responsibility.
         */
        chatMessage.setType(ChatMessage.MessageType.CHAT);

        /**
         * Save to H2 database.
         *
         * messageRepository.save(entity) runs:
         *   INSERT INTO chat_messages (content, sender, type, sent_at)
         *   VALUES ('Hello', 'alice', 'CHAT', '2024-01-15 14:32:00')
         *
         * The returned savedMessage has the auto-generated id populated.
         * The id was null before save(), now it has a value like 1, 2, 3...
         *
         * We return savedMessage (not chatMessage) because savedMessage
         * has the id set. The client receives the id back, which is useful
         * for editing/deleting messages in later phases.
         */
        ChatMessage savedMessage = messageRepository.save(chatMessage);

        /**
         * The return value is automatically sent to /topic/chatroom
         * by @SendTo. Every subscriber receives:
         * {
         *   "id": 1,
         *   "content": "Hello",
         *   "sender": "alice",
         *   "type": "CHAT",
         *   "sentAt": "2024-01-15T14:32:00"
         * }
         */
        return savedMessage;
    }

    /**
     * addUser() — Handles a user joining the chatroom.
     *
     * Called when a client first connects and announces themselves.
     * In Phase 1's JavaScript:
     *   broadcastEvent('JOIN', username + ' joined the chat')
     *   which calls: socket.send(JSON.stringify({ type: 'JOIN', sender: username, content: '...' }))
     *
     * We:
     *   1. Store the username in the WebSocket session attributes
     *      (so WebSocketEventListener can retrieve it on disconnect)
     *   2. Create a JOIN message with server timestamp
     *   3. Save it to the database
     *   4. Broadcast it to all subscribers
     *
     * @MessageMapping("/chat.addUser")
     *   Handles STOMP messages to /app/chat.addUser.
     *
     * SimpMessageHeaderAccessor headerAccessor
     *   Gives access to the STOMP session headers and attributes.
     *   We use it to store the username in the session:
     *     headerAccessor.getSessionAttributes().put("username", sender)
     *   This Map persists for the life of the WebSocket connection.
     *   WebSocketEventListener reads from it when the user disconnects.
     */
    @MessageMapping("/chat.addUser")
    @SendTo("/topic/chatroom")
    public ChatMessage addUser(
        @Payload ChatMessage chatMessage,
        SimpMessageHeaderAccessor headerAccessor
    ) {
        /**
         * Store username in WebSocket session attributes.
         *
         * The session attributes Map lives for the duration of this
         * WebSocket connection. When the connection closes, Spring fires
         * SessionDisconnectEvent — our WebSocketEventListener retrieves
         * the username from this Map to broadcast the LEAVE notification.
         *
         * Without storing it here, we'd have no way to know WHO left
         * when the disconnect event fires (there's no Principal available there).
         */
        String sender = chatMessage.getSender();
        headerAccessor.getSessionAttributes().put("username", sender);

        // Build a clean JOIN message with server-controlled values
        ChatMessage joinMessage = ChatMessage.builder()
            .type(ChatMessage.MessageType.JOIN)
            .sender(sender)
            .content(sender + " joined the chat")
            .sentAt(LocalDateTime.now())
            .build();

        // Persist — JOIN events are saved so history shows when users joined
        messageRepository.save(joinMessage);

        // @SendTo broadcasts this to all subscribers of /topic/chatroom
        return joinMessage;
    }
}
