package com.chatroom.controller;

import com.chatroom.model.ChatMessage;
import com.chatroom.service.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.LocalDateTime;

/**
 * ChatController — Handles incoming STOMP messages from WebSocket clients.
 *
 * This is NOT a @RestController (which handles HTTP requests).
 * It is a @Controller that handles STOMP messages.
 *
 * Think of @MessageMapping like @RequestMapping, but for WebSocket messages:
 *   - @RequestMapping("/api/chat") handles HTTP GET/POST to /api/chat
 *   - @MessageMapping("/chat.send") handles STOMP SEND frames to /app/chat.send
 *
 * The "/app" prefix is stripped by Spring (configured in WebSocketConfig)
 * before matching against @MessageMapping paths.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * @RequiredArgsConstructor (Lombok)
 *   Generates a constructor with all 'final' fields as parameters.
 *   This enables "constructor injection" — the recommended way to inject
 *   dependencies in Spring (over @Autowired field injection).
 *
 *   Generated constructor:
 *     public ChatController(ChatMessageRepository repo, SimpMessagingTemplate template) {
 *         this.messageRepository = repo;
 *         this.messagingTemplate = template;
 *     }
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Controller
@RequiredArgsConstructor
public class ChatController {

    /**
     * messageRepository — Our JPA repository for saving/loading messages.
     * 'final' + @RequiredArgsConstructor = Spring injects this automatically.
     */
    private final ChatMessageRepository messageRepository;

    /**
     * messagingTemplate — Spring's tool for sending messages FROM the server
     * to subscribed clients programmatically (outside of a @MessageMapping method).
     *
     * Use cases:
     *   - convertAndSend("/topic/chatroom", msg) → broadcast to all subscribers
     *   - convertAndSendToUser("alice", "/queue/private", msg) → send to one user
     *
     * We use it in the addUser() method to send a JOIN notification.
     */
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * sendMessage() — Handles a new chat message from a client.
     *
     * Message flow:
     *   1. Client calls: stompClient.publish({ destination: '/app/chat.send', body: JSON })
     *   2. Spring routes the STOMP SEND frame here (after stripping "/app")
     *   3. We set the sender and timestamp (never trust client-provided values)
     *   4. We save the message to the H2 database
     *   5. @SendTo broadcasts the message to ALL subscribers of /topic/chatroom
     *
     * @MessageMapping("/chat.send")
     *   Handles STOMP messages sent to destination: /app/chat.send
     *
     * @SendTo("/topic/chatroom")
     *   After this method returns, Spring automatically sends the return value
     *   to /topic/chatroom. Every client subscribed to that topic receives it.
     *   This is the "broadcast" pattern.
     *
     * @Payload ChatMessage chatMessage
     *   Spring deserializes the STOMP message body (JSON) into a ChatMessage object.
     *   Jackson handles the JSON → Java conversion automatically.
     *
     * Principal principal
     *   Spring injects the currently authenticated user.
     *   principal.getName() returns the username (e.g., "alice").
     *   This is how we override the sender — we IGNORE what the client says
     *   and use the authenticated user's name instead. Security!
     */
    @MessageMapping("/chat.send")
    @SendTo("/topic/chatroom")
    public ChatMessage sendMessage(
        @Payload ChatMessage chatMessage,
        Principal principal
    ) {
        /**
         * Security: ALWAYS set sender from the authenticated Principal.
         * Never use chatMessage.getSender() as sent by the client —
         * a malicious client could impersonate another user.
         */
        chatMessage.setSender(principal.getName());

        /**
         * Set the server-side timestamp.
         * Don't trust client timestamps — use server time for consistent ordering.
         */
        chatMessage.setSentAt(LocalDateTime.now());

        /**
         * Ensure message type is CHAT (not JOIN/LEAVE — those are handled below).
         * Even if the client sends type=JOIN, we override it here.
         */
        chatMessage.setType(ChatMessage.MessageType.CHAT);

        /**
         * Persist to database.
         * messageRepository.save() runs:
         *   INSERT INTO chat_messages (content, sender, type, sent_at) VALUES (?, ?, ?, ?)
         *
         * The returned 'savedMessage' has the auto-generated 'id' populated.
         * We return the saved message (with ID and timestamp) — not the input.
         */
        ChatMessage savedMessage = messageRepository.save(chatMessage);

        /**
         * @SendTo("/topic/chatroom") automatically sends the return value
         * to all subscribers of /topic/chatroom.
         * We return savedMessage (which has the server-set id, sender, sentAt).
         */
        return savedMessage;
    }

    /**
     * addUser() — Handles a user joining the chatroom.
     *
     * When a user connects and their Vue.js app subscribes to the chatroom,
     * the client sends a STOMP frame to /app/chat.addUser.
     *
     * We:
     *   1. Look up the authenticated username
     *   2. Create a JOIN notification message
     *   3. Save it to the database
     *   4. Broadcast it to all subscribers
     *
     * @MessageMapping("/chat.addUser")
     *   Handles STOMP messages sent to: /app/chat.addUser
     *
     * SimpMessageHeaderAccessor headerAccessor
     *   Gives us access to the STOMP session headers.
     *   We use it to store the username in the WebSocket session attributes —
     *   this lets us retrieve the username when the user disconnects
     *   (in the WebSocketEventListener below, where Principal might not be available).
     */
    @MessageMapping("/chat.addUser")
    @SendTo("/topic/chatroom")
    public ChatMessage addUser(
        @Payload ChatMessage chatMessage,
        SimpMessageHeaderAccessor headerAccessor,
        Principal principal
    ) {
        /**
         * Store username in the WebSocket session.
         * headerAccessor.getSessionAttributes() returns the session's attribute map.
         * We put the username in it so WebSocketEventListener can retrieve it
         * when the disconnect event fires (where Principal may not be available).
         */
        headerAccessor.getSessionAttributes().put("username", principal.getName());

        // Create a properly-formed JOIN message
        ChatMessage joinMessage = ChatMessage.builder()
            .type(ChatMessage.MessageType.JOIN)
            .sender(principal.getName())
            .content(principal.getName() + " joined the chat")
            .sentAt(LocalDateTime.now())
            .build();

        // Persist the JOIN event (useful for chat history — "Alice joined")
        messageRepository.save(joinMessage);

        return joinMessage;
    }
}
