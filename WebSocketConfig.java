package com.chatroom.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocketConfig — Configures the entire WebSocket + STOMP messaging infrastructure.
 *
 * This is the single most important configuration class in the backend.
 * It answers three questions:
 *   1. WHERE do clients connect? (the WebSocket endpoint URL)
 *   2. WHERE does the in-memory broker live? (topic/queue prefixes)
 *   3. WHERE do client-to-server messages go? (application destination prefix)
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * HOW MESSAGES FLOW (summarized):
 *
 *   Client SEND  →  /app/chat.send  →  @MessageMapping  →  /topic/chatroom
 *                                                          ↓
 *                                                   In-Memory Broker
 *                                                          ↓
 *                               All clients subscribed to /topic/chatroom
 *
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * @Configuration  — Marks this as a Spring configuration class (defines beans/settings).
 * @EnableWebSocketMessageBroker — Turns on the full STOMP-over-WebSocket support.
 *   Without this annotation, none of the WebSocket routing would work.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * configureMessageBroker() — Sets up the message broker and destination prefixes.
     *
     * The MessageBrokerRegistry is the routing table for all STOMP messages.
     * Think of it like configuring a post office:
     *   - Some addresses (prefixes) go to the in-memory broker
     *   - Some addresses go to your @Controller methods
     *
     * @param registry  Spring passes this in — we call methods on it to configure routing.
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {

        /**
         * enableSimpleBroker("/topic", "/queue")
         *
         * Enables the in-memory (Simple) broker for these destination prefixes.
         *
         * The Simple broker is a Map inside the Spring application — it stores
         * a list of subscribers per destination and fans out messages to them.
         *
         * "/topic" — Used for pub/sub (broadcast to all subscribers).
         *            Example: /topic/chatroom → every client in the room gets the message.
         *
         * "/queue" — Used for point-to-point (one-to-one) messaging.
         *            Example: /queue/notifications → only a specific user gets the message.
         *
         * When a client sends to a topic/queue destination, the broker receives
         * it and immediately relays it to all matching subscribers.
         */
        registry.enableSimpleBroker("/topic", "/queue");

        /**
         * setApplicationDestinationPrefixes("/app")
         *
         * Messages sent to destinations starting with "/app" are routed to
         * @MessageMapping methods in your @Controller classes FIRST.
         * The controller processes the message (validates, saves to DB, etc.)
         * and then decides whether/how to forward it to the broker.
         *
         * Example flow:
         *   Client sends to   → /app/chat.send
         *   Spring strips /app → chat.send
         *   Routes to method  → @MessageMapping("/chat.send")
         *   Method sends to   → /topic/chatroom  (via SimpMessagingTemplate)
         *   Broker delivers   → all subscribers of /topic/chatroom
         *
         * This separation is important: messages go through your code before
         * being broadcast. You can validate, log, enrich, or block them.
         */
        registry.setApplicationDestinationPrefixes("/app");

        /**
         * setUserDestinationPrefix("/user")
         *
         * Enables user-specific destinations.
         * When you use convertAndSendToUser("username", "/queue/reply", message),
         * Spring automatically routes to /user/{sessionId}/queue/reply,
         * ensuring only that specific connected client receives the message.
         *
         * This is how private/direct messages work.
         */
        registry.setUserDestinationPrefix("/user");
    }

    /**
     * registerStompEndpoints() — Registers the WebSocket handshake endpoint.
     *
     * This is the URL that clients connect to when establishing a WebSocket connection.
     * Before any STOMP messages flow, the client must do an HTTP upgrade handshake
     * at this endpoint URL.
     *
     * @param registry  Spring passes this in — we call addEndpoint() to register URLs.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {

        registry
            /**
             * addEndpoint("/ws")
             *
             * Registers /ws as the WebSocket handshake URL.
             * The client connects to: ws://localhost:8080/ws
             *
             * Internally, Spring creates an HTTP endpoint at /ws.
             * When a client hits it, Spring performs the HTTP → WebSocket upgrade
             * (defined in RFC 6455 Section 4).
             */
            .addEndpoint("/ws")

            /**
             * setAllowedOriginPatterns("*")
             *
             * Configures CORS for the WebSocket handshake.
             *
             * During the HTTP upgrade, the browser sends an Origin header.
             * Spring checks it against this allowlist.
             *
             * "*" allows any origin — fine for learning.
             * In production, replace with your actual domain:
             *   .setAllowedOriginPatterns("https://yourchatapp.com")
             *
             * This is different from the CORS config in SecurityConfig,
             * which covers REST endpoints. WebSocket handshake has its own CORS.
             */
            .setAllowedOriginPatterns("*")

            /**
             * withSockJS()
             *
             * Enables SockJS as a fallback transport layer.
             *
             * SockJS is a JavaScript protocol that tries WebSocket first,
             * then falls back to HTTP streaming, then HTTP long-polling.
             * This is important because some corporate firewalls or older browsers
             * block WebSocket (port 80/443 upgrade).
             *
             * With SockJS enabled:
             *   - Spring serves SockJS info at /ws/info
             *   - Spring negotiates the best available transport per client
             *   - Your application code doesn't change — SockJS is transparent
             *
             * On the Vue.js side, we use the 'sockjs-client' npm package
             * which automatically handles this negotiation.
             */
            .withSockJS();
    }
}
