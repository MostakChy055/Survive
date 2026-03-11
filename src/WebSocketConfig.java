package com.mostak.chatroom.config;

// ─────────────────────────────────────────────────────────────────────────────
// IMPORTS
// Java needs to know exactly which classes we're using.
// IntelliJ will show errors if these are missing — use Alt+Enter to auto-import,
// or just paste this file as-is and IntelliJ resolves them from pom.xml.
// ─────────────────────────────────────────────────────────────────────────────
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocketConfig — The heart of Phase 2.
 *
 * This single class does three things:
 *   1. Tells Spring: "I want WebSocket + STOMP support" (@EnableWebSocketMessageBroker)
 *   2. Registers the URL clients connect to (/ws)
 *   3. Configures the in-memory message broker (routing rules)
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHAT IS A MESSAGE BROKER?
 *
 * Think of it as a post office inside your Spring Boot app.
 * When User A sends a message, it doesn't go directly to User B.
 * It goes to the broker, which has a list of everyone subscribed
 * to that destination, and delivers it to all of them.
 *
 *   User A sends → /app/chat.send
 *                        ↓
 *              ChatController processes it
 *                        ↓
 *              Broker receives it at /topic/chatroom
 *                        ↓
 *         Broker delivers to ALL subscribers of /topic/chatroom
 *                        ↓
 *              User B, C, D all receive the message
 *
 * In Phase 2, the broker is "in-memory" — it's just a HashMap inside
 * the Spring Boot process. Fast, simple, no extra setup.
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * @Configuration
 *   Marks this class as a Spring configuration source.
 *   Spring scans for @Configuration classes at startup and reads them.
 *
 * @EnableWebSocketMessageBroker
 *   Activates the full STOMP-over-WebSocket stack.
 *   Without this annotation, none of the WebSocket routing works.
 *   It's like a power switch for the entire feature.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * configureMessageBroker()
     *
     * Sets up the routing rules — which destinations go where.
     *
     * Think of destinations like mailing addresses.
     * This method defines the address rules:
     *   "/topic/**" and "/queue/**" → delivered by the in-memory broker
     *   "/app/**"                   → handled by a @Controller method first
     *
     * @param registry
     *   Spring passes this in automatically.
     *   We call methods on it to configure routing.
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {

        /**
         * enableSimpleBroker("/topic", "/queue")
         *
         * Activates the in-memory broker for these prefixes.
         *
         * "/topic" → pub/sub (one message goes to MANY subscribers)
         *            Used for: group chat, broadcast announcements
         *            Example: /topic/chatroom → every user in the room gets it
         *
         * "/queue" → point-to-point (one message goes to ONE user)
         *            Used for: private messages, notifications
         *            Example: /queue/user-123 → only user 123 gets it
         *
         * In Phase 2 we only use /topic/chatroom (broadcast to everyone).
         * We add /queue for private messages in a later phase.
         */
        registry.enableSimpleBroker("/topic", "/queue");

        /**
         * setApplicationDestinationPrefixes("/app")
         *
         * Messages sent to /app/anything are routed to
         * @MessageMapping methods in your @Controller classes.
         *
         * The flow:
         *   Client sends to → /app/chat.send
         *   Spring strips /app → looks for @MessageMapping("/chat.send")
         *   That method runs (validates, saves, enriches)
         *   Method outputs to → /topic/chatroom
         *   Broker delivers to all subscribers
         *
         * WHY not send directly to /topic/chatroom?
         *   Because we want server-side control:
         *     - Override the sender with the authenticated user
         *     - Set the server timestamp
         *     - Save to database
         *     - Validate/filter content
         *   If clients could write directly to /topic, they could
         *   send as anyone with any content — a security hole.
         */
        registry.setApplicationDestinationPrefixes("/app");
    }

    /**
     * registerStompEndpoints()
     *
     * Registers the URL that clients use to establish the WebSocket connection.
     *
     * This is the HANDSHAKE endpoint — not where messages are sent.
     * Think of it like a door: clients knock here first, then once
     * inside (connected), they send messages to destinations like /app/chat.send.
     *
     * @param registry
     *   Spring passes this in. We call addEndpoint() on it.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {

        registry
            /**
             * addEndpoint("/ws")
             *
             * Registers /ws as the WebSocket handshake URL.
             *
             * In Phase 1's HTML, you'll change the URL from:
             *   wss://echo.websocket.org
             * to:
             *   ws://localhost:8080/ws
             *
             * That "/ws" is what we're registering here.
             *
             * When the browser hits ws://localhost:8080/ws, Spring performs
             * the HTTP → WebSocket upgrade (HTTP 101 Switching Protocols).
             * After that, the connection is a live WebSocket.
             */
            .addEndpoint("/ws")

            /**
             * setAllowedOriginPatterns("*")
             *
             * Allows any origin to connect via WebSocket.
             *
             * WHAT IS AN ORIGIN?
             *   An origin = protocol + domain + port.
             *   http://localhost:5173 is a different origin from http://localhost:8080.
             *
             * When a browser makes a WebSocket connection, it sends an "Origin" header.
             * Spring checks this against the allowed list.
             * "*" = allow everyone.
             *
             * WHY we need this:
             *   Your HTML file opened from the filesystem (file:///...)
             *   has a null/different origin from localhost:8080.
             *   Without this, Spring would reject the connection.
             *
             * In production: replace "*" with your actual domain.
             *   .setAllowedOriginPatterns("https://mychatapp.com")
             */
            .setAllowedOriginPatterns("*")

            /**
             * withSockJS()
             *
             * Enables SockJS as a transport fallback.
             *
             * WHAT IS SOCKJS?
             *   A JavaScript protocol that tries:
             *     1. Native WebSocket (best)
             *     2. HTTP streaming (fallback)
             *     3. HTTP long-polling (last resort)
             *
             *   Some corporate networks/firewalls block WebSocket upgrades.
             *   SockJS makes your app work even then.
             *
             * WHY add it now?
             *   Because @stomp/stompjs (which we use in Phase 4)
             *   expects SockJS. Adding it now means no changes later.
             *
             * NOTE FOR PHASE 2:
             *   Our Phase 1 HTML uses the raw WebSocket API, not SockJS.
             *   So we need to connect WITHOUT SockJS in Phase 2's HTML.
             *   In Phase 4 when we add @stomp/stompjs, we'll use SockJS.
             *   That's why the Phase 2 URL is ws://localhost:8080/ws/websocket
             *   (the /websocket suffix bypasses SockJS negotiation).
             */
            .withSockJS();
    }
}
