package com.chatroom.controller;

import com.chatroom.model.ChatMessage;
import com.chatroom.service.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * MessageRestController — Handles regular HTTP REST requests.
 *
 * We need REST endpoints (not WebSocket) for:
 *   1. Loading chat history when a user first opens the chatroom
 *   2. Checking if the current session is authenticated (/api/auth/me)
 *
 * WHY REST and not WebSocket for history?
 *   WebSocket is designed for real-time streaming of NEW events.
 *   Loading existing history is a one-time request/response — perfect for REST.
 *   Mixing history loading into WebSocket would complicate the protocol.
 *
 * @RestController — Combines @Controller + @ResponseBody.
 *   Every method return value is automatically serialized to JSON
 *   and written to the HTTP response body.
 *
 * @RequestMapping("/api") — All endpoints in this class are prefixed with /api.
 *   So @GetMapping("/messages") becomes GET /api/messages.
 *
 * @RequiredArgsConstructor — Constructor injection for final fields.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MessageRestController {

    private final ChatMessageRepository messageRepository;

    /**
     * getMessageHistory() — Returns the last 50 messages.
     *
     * Called by Vue.js when the chatroom component mounts (page load).
     * Displays the recent message history so users aren't dropped into
     * an empty room with no context.
     *
     * GET /api/messages
     *
     * @GetMapping("/messages") — Maps HTTP GET /api/messages to this method.
     *
     * ResponseEntity<List<ChatMessage>> — Wraps the response in an HTTP response.
     *   ResponseEntity.ok(data) returns:
     *     HTTP Status: 200 OK
     *     Body: JSON array of ChatMessage objects
     *
     * Requires authentication (configured in SecurityConfig):
     *   - If not logged in → Spring Security returns 401 before this method runs
     *   - If logged in → this method executes normally
     */
    @GetMapping("/messages")
    public ResponseEntity<List<ChatMessage>> getMessageHistory() {
        List<ChatMessage> messages = messageRepository.findTop50ByOrderBySentAtAsc();

        /**
         * ResponseEntity.ok(messages) builds:
         *   HTTP/1.1 200 OK
         *   Content-Type: application/json
         *   Body: [{"id":1,"content":"Hello","sender":"alice",...}, ...]
         */
        return ResponseEntity.ok(messages);
    }

    /**
     * getCurrentUser() — Returns the currently authenticated user's info.
     *
     * GET /api/auth/me
     *
     * Vue.js calls this on app startup to check:
     *   a) Is there an active session? (Did the user already log in earlier?)
     *   b) If yes, who are they? (So we can show their username in the UI)
     *
     * Principal — Spring Security automatically injects the authenticated user.
     *   If no user is authenticated, Spring Security returns 401 before this runs.
     *   If authenticated, principal.getName() returns the username.
     *
     * Map.of() — Creates an immutable map (Java 9+).
     *   Jackson serializes it to: {"username": "alice", "authenticated": true}
     */
    @GetMapping("/auth/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(Principal principal) {
        return ResponseEntity.ok(Map.of(
            "username", principal.getName(),
            "authenticated", true
        ));
    }
}
