package com.mostak.chatroom.service;

import com.mostak.chatroom.model.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * ChatMessageListener — Consumes messages FROM RabbitMQ and
 * broadcasts them TO all connected WebSocket clients.
 *
 * ─────────────────────────────────────────────────────────────────
 * THIS IS THE MISSING PIECE.
 *
 * Without this class, the flow was:
 *   Browser → Spring Boot → RabbitMQ (dead end — messages pile up)
 *
 * With this class, the flow is complete:
 *   Browser → Spring Boot → RabbitMQ → THIS CLASS → All Browsers
 *
 * ─────────────────────────────────────────────────────────────────
 * WHY ROUTE THROUGH RABBITMQ AT ALL?
 *
 * You might wonder: ChatController already broadcasts via @SendTo.
 * Why go through RabbitMQ and broadcast AGAIN here?
 *
 * The answer is SCALABILITY.
 *
 * Imagine you run TWO Spring Boot instances (two servers):
 *
 *   Instance A          Instance B
 *   alice connected     bob connected
 *
 * Alice sends a message → hits Instance A → @SendTo broadcasts...
 * but only to Instance A's subscribers. Bob is on Instance B.
 * Bob never sees Alice's message.
 *
 * With RabbitMQ in the middle:
 *   Alice → Instance A → RabbitMQ queue
 *                              ↓
 *                    BOTH instances listen
 *                    BOTH broadcast to their own subscribers
 *                    Bob (on Instance B) gets the message ✓
 *
 * This is exactly Option B from the architecture discussion.
 * Even with one instance (right now), building it this way
 * means you can scale later with zero code changes.
 *
 * ─────────────────────────────────────────────────────────────────
 * WHY THIS ALSO FIXES THE CONNECTION ISSUE:
 *
 * @RabbitListener makes Spring open a connection to RabbitMQ
 * IMMEDIATELY at startup — it needs to start listening right away.
 * RabbitTemplate (used in ChatMessageProducer) is lazy — it only
 * connects when you first call convertAndSend().
 *
 * So adding @RabbitListener is not just a workaround —
 * it's the correct and complete solution.
 * ─────────────────────────────────────────────────────────────────
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatMessageListener {

    /**
     * SimpMessagingTemplate — Spring's tool for sending messages
     * to WebSocket subscribers FROM anywhere in the application.
     *
     * We used @SendTo in ChatController, which only works as an
     * annotation on @MessageMapping methods.
     *
     * SimpMessagingTemplate works ANYWHERE — services, listeners,
     * scheduled tasks, event handlers. It's more flexible.
     *
     * convertAndSend(destination, payload):
     *   - Serializes payload to JSON
     *   - Sends to the in-memory WebSocket broker
     *   - Broker delivers to ALL subscribers of that destination
     */
    private final SimpMessagingTemplate messagingTemplate;


    /**
     * consumeMessage() — Reads a message from RabbitMQ and
     * broadcasts it to all connected WebSocket clients.
     *
     * @RabbitListener(queues = "${chat.queue}")
     *
     *   This annotation does three things:
     *
     *   1. At startup: Spring opens a connection to RabbitMQ
     *      and registers a consumer on "chat.queue".
     *      This is why the connection now appears immediately.
     *
     *   2. Continuously: Spring polls chat.queue in the background.
     *      The moment a message arrives in the queue,
     *      this method is called automatically.
     *
     *   3. Deserialization: Jackson2JsonMessageConverter (configured
     *      in RabbitMQConfig) converts the JSON bytes from RabbitMQ
     *      back into a ChatMessage Java object automatically.
     *      You receive a fully populated ChatMessage, not raw bytes.
     *
     *   "${chat.queue}" reads "chat.queue" from application.properties.
     *   Same value we used everywhere else — "chat.queue".
     *
     * @param chatMessage
     *   The fully deserialized ChatMessage from RabbitMQ.
     *   It has all fields: id, content, sender, type, sentAt.
     *   These were set in ChatController before publishing.
     */
    @RabbitListener(queues = "${chat.queue}")
    public void consumeMessage(ChatMessage chatMessage) {

        log.info(
            "Consumed from RabbitMQ — sender: {}, type: {}, content: {}",
            chatMessage.getSender(),
            chatMessage.getType(),
            chatMessage.getContent()
        );

        /**
         * Broadcast to ALL WebSocket subscribers of /topic/chatroom.
         *
         * Every browser that has subscribed to /topic/chatroom
         * will receive this message instantly.
         *
         * This is the same destination that @SendTo("/topic/chatroom")
         * uses in ChatController — so both paths lead to the same place.
         *
         * In single-instance mode (right now):
         *   The message flows: ChatController → RabbitMQ → here → browser
         *   It's slightly redundant with @SendTo but sets up the
         *   correct architecture for multi-instance scaling.
         *
         * In multi-instance mode (future):
         *   Remove @SendTo from ChatController entirely.
         *   ONLY broadcast from here.
         *   Now all instances broadcast to their own subscribers
         *   and every user gets every message regardless of which
         *   server instance they're connected to.
         */
        messagingTemplate.convertAndSend("/topic/chatroom", chatMessage);

        log.info(
            "Broadcasted to /topic/chatroom — id: {}",
            chatMessage.getId()
        );
    }
}
