package com.mostak.chatroom.controller;

import com.mostak.chatroom.model.ChatMessage;
import com.mostak.chatroom.service.ChatMessageProducer;
import com.mostak.chatroom.service.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * TestController — A simple REST controller to test the full pipeline
 * without needing any frontend or WebSocket client.
 *
 * ─────────────────────────────────────────────────────────────────
 * HOW TO USE:
 *
 * Once Spring Boot is running, open your browser and visit:
 *
 *   http://localhost:8080/test/send?message=hello&sender=alice
 *
 * This triggers the FULL pipeline:
 *   1. Creates a ChatMessage
 *   2. Saves it to H2 database
 *   3. Publishes it to RabbitMQ
 *   4. ChatMessageListener consumes it from RabbitMQ
 *   5. Broadcasts to /topic/chatroom WebSocket subscribers
 *
 * You verify it worked by:
 *   A. Watching IntelliJ logs (easiest)
 *   B. Checking RabbitMQ console
 *   C. Checking H2 console
 *
 * All three are explained below.
 * ─────────────────────────────────────────────────────────────────
 *
 * This controller is FOR TESTING ONLY.
 * Remove it (or add a @Profile("dev") annotation) before production.
 */
@RestController
@RequestMapping("/test")
@Slf4j
@RequiredArgsConstructor
public class TestController {

    private final ChatMessageProducer chatMessageProducer;
    private final ChatMessageRepository chatMessageRepository;


    /**
     * sendTestMessage() — Triggers the full pipeline via a browser URL.
     *
     * GET /test/send?message=hello&sender=alice
     *
     * @RequestParam String message
     *   Reads the "message" query parameter from the URL.
     *   defaultValue = "test message" means if you visit
     *   /test/send with no parameters, it still works.
     *
     * @RequestParam String sender
     *   Reads the "sender" query parameter.
     *   defaultValue = "testuser"
     *
     * Returns a JSON response showing what was sent,
     * so you can see confirmation right in the browser.
     */
    @GetMapping("/send")
    public ResponseEntity<Map<String, Object>> sendTestMessage(
        @RequestParam(defaultValue = "test message") String message,
        @RequestParam(defaultValue = "testuser") String sender
    ) {
        log.info("=== TEST ENDPOINT HIT — sender: {}, message: {}", sender, message);

        // Step 1 — Build a ChatMessage exactly like ChatController does
        ChatMessage chatMessage = ChatMessage.builder()
                .content(message)
                .sender(sender)
                .type(ChatMessage.MessageType.CHAT)
                .sentAt(LocalDateTime.now())
                .build();

        // Step 2 — Save to H2 database (assigns the ID)
        ChatMessage savedMessage = chatMessageRepository.save(chatMessage);
        log.info("Saved to H2 — id: {}", savedMessage.getId());

        // Step 3 — Publish to RabbitMQ
        // You will see in IntelliJ logs:
        //   [INFO] Publishing to RabbitMQ — exchange: chat.exchange...
        //   [INFO] Published successfully — id: 1
        // Then immediately after (from ChatMessageListener):
        //   [INFO] Consumed from RabbitMQ — sender: alice...
        //   [INFO] Broadcasted to /topic/chatroom — id: 1
        chatMessageProducer.publishMessage(savedMessage);

        // Step 4 — Return a confirmation JSON to the browser
        // The browser shows this so you know the request completed
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "Pipeline triggered successfully",
            "id", savedMessage.getId(),
            "sender", savedMessage.getSender(),
            "content", savedMessage.getContent(),
            "sentAt", savedMessage.getSentAt().toString(),
            "flow", "H2 saved → RabbitMQ published → Listener consumed → WebSocket broadcast"
        ));
    }


    /**
     * checkDatabase() — Shows the last 10 messages stored in H2.
     *
     * GET /test/messages
     *
     * Use this to confirm messages are actually being saved to the database.
     * Visit: http://localhost:8080/test/messages
     */
    @GetMapping("/messages")
    public ResponseEntity<?> checkDatabase() {
        var messages = chatMessageRepository.findTop50ByOrderBySentAtAsc();
        log.info("=== DATABASE CHECK — found {} messages", messages.size());
        return ResponseEntity.ok(Map.of(
            "count", messages.size(),
            "messages", messages
        ));
    }


    /**
     * ping() — The simplest possible check.
     *
     * GET /test/ping
     *
     * If Spring Boot is running and this returns {"status":"ok"},
     * then at minimum the web layer is working.
     * Use this first to confirm the app started correctly.
     */
    @GetMapping("/ping")
    public ResponseEntity<Map<String, String>> ping() {
        log.info("=== PING ===");
        return ResponseEntity.ok(Map.of(
            "status", "ok",
            "message", "Spring Boot is running"
        ));
    }
}
