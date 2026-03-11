package com.mostak.chatroom.controller;

import com.mostak.chatroom.model.ChatMessage;
import com.mostak.chatroom.service.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * MessageRestController — Handles regular HTTP REST requests.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WAIT — IF WE HAVE WEBSOCKET, WHY DO WE ALSO NEED HTTP?
 *
 * Great question. WebSocket is for REAL-TIME events — things happening NOW.
 * HTTP is for REQUEST/RESPONSE data — things that already exist.
 *
 * When a user first opens the chatroom:
 *   - They need to see the last 50 messages (history)
 *   - Those messages already exist in the database
 *   - This is a one-time data load, not a stream of live events
 *   → Perfect for HTTP GET
 *
 * WebSocket handles:
 *   - New messages arriving in real time
 *   - JOIN/LEAVE events
 *   - Any event that needs to be pushed immediately to all users
 *
 * HTTP handles:
 *   - Loading history (one-time, not real-time)
 *   - Login/logout (Phase 5)
 *   - Any request/response operation
 *
 * The rule: use the right tool for the job.
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * @RestController
 *   = @Controller + @ResponseBody
 *   Every method return value is automatically serialized to JSON
 *   and written as the HTTP response body.
 *   No need to manually call response.getWriter().write(json).
 *
 * @RequestMapping("/api")
 *   All endpoints in this class are prefixed with /api.
 *   So @GetMapping("/messages") becomes: GET /api/messages
 *   The /api prefix clearly separates backend API calls from
 *   any static files (HTML, CSS, JS) we might serve.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MessageRestController {

    private final ChatMessageRepository messageRepository;

    /**
     * getMessageHistory() — Returns the last 50 chat messages.
     *
     * HTTP endpoint: GET /api/messages
     *
     * Called by the frontend when the chatroom first loads.
     * Shows users the recent history so they have context
     * before new real-time messages start arriving.
     *
     * @GetMapping("/messages")
     *   Maps HTTP GET requests to /api/messages → this method.
     *   Only GET — no POST, PUT, DELETE here.
     *
     * ResponseEntity<List<ChatMessage>>
     *   ResponseEntity wraps the response with:
     *     - HTTP status code (200, 404, etc.)
     *     - HTTP headers
     *     - Body (our list of messages)
     *
     *   ResponseEntity.ok(messages) creates:
     *     HTTP/1.1 200 OK
     *     Content-Type: application/json
     *     Body: [{"id":1,"content":"Hello","sender":"alice",...}, ...]
     *
     *   Using ResponseEntity is better than returning List<ChatMessage> directly
     *   because it gives us explicit control over the HTTP status code.
     *   Later we can return 404, 401, etc. with the right body.
     *
     * ─────────────────────────────────────────────────────────────────────────
     * NOTE — Phase 2 has NO authentication.
     *
     * Anyone can call GET /api/messages without logging in.
     * This is intentional for Phase 2 — we focus on WebSocket first.
     * Phase 5 adds Spring Security which locks this down.
     * ─────────────────────────────────────────────────────────────────────────
     */
    @GetMapping("/messages")
    public ResponseEntity<List<ChatMessage>> getMessageHistory() {

        /**
         * findTop50ByOrderBySentAtAsc()
         * Defined in ChatMessageRepository.
         * Spring Data JPA generates the SQL automatically from the method name.
         * Returns: list of last 50 messages, oldest first.
         */
        List<ChatMessage> messages = messageRepository.findTop50ByOrderBySentAtAsc();

        return ResponseEntity.ok(messages);
    }
}
