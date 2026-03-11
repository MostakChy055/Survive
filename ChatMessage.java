package com.chatroom.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ChatMessage — Represents a single message in the chatroom.
 *
 * This class serves TWO purposes:
 *   1. JPA Entity → gets persisted to the H2 database (messages are saved)
 *   2. STOMP payload → gets serialized to/from JSON over WebSocket
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * LOMBOK ANNOTATIONS (eliminates boilerplate):
 *
 * @Data             → generates: getters, setters, equals(), hashCode(), toString()
 * @Builder          → generates: ChatMessage.builder().sender("alice").content("hi").build()
 * @NoArgsConstructor → generates: new ChatMessage() (required by JPA and Jackson)
 * @AllArgsConstructor → generates: new ChatMessage(id, content, sender, ...) (required by @Builder)
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * JPA ANNOTATIONS:
 *
 * @Entity  → Marks this class as a JPA-managed entity. Hibernate will create
 *            a "chat_message" table (snake_case of class name by default).
 *
 * @Table(name = "chat_messages") → Explicitly names the table "chat_messages".
 *            Explicit naming is clearer than relying on defaults.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    /**
     * @Id — Marks this field as the primary key of the table.
     *
     * @GeneratedValue(strategy = GenerationType.IDENTITY)
     * Tells Hibernate to let the database auto-increment this column.
     * For H2, this generates: id BIGINT AUTO_INCREMENT PRIMARY KEY
     *
     * We use Long (not int) because message IDs can grow very large over time.
     * BIGINT supports up to 9,223,372,036,854,775,807 — essentially unlimited.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * content — The actual text of the message.
     *
     * @Column(nullable = false, length = 2000)
     * nullable = false → database-level NOT NULL constraint (message must have content)
     * length = 2000    → VARCHAR(2000) in the database
     *                   Standard chat message length limit (Twitter is 280,
     *                   Slack allows 4000, Discord 2000 — we match Discord).
     */
    @Column(nullable = false, length = 2000)
    private String content;

    /**
     * sender — The username of the person who sent this message.
     *
     * In a more complex app, this would be a @ManyToOne foreign key to a User entity.
     * For simplicity here, we store just the username string.
     *
     * nullable = false → a message must always have a sender.
     */
    @Column(nullable = false)
    private String sender;

    /**
     * type — The category of this message.
     *
     * @Enumerated(EnumType.STRING) — Stores the enum as a string in the DB
     *   ("CHAT", "JOIN", "LEAVE") rather than an integer (0, 1, 2).
     *   String storage is more readable when querying the DB directly
     *   and survives enum reordering without data corruption.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageType type;

    /**
     * sentAt — When the message was created on the server.
     *
     * We record time SERVER-SIDE (not trusting the client's clock).
     * This ensures consistent ordering even if client clocks are wrong.
     *
     * LocalDateTime is timezone-naive (no timezone offset stored).
     * In production, use Instant or ZonedDateTime (UTC) for timezone correctness.
     */
    @Column(nullable = false)
    private LocalDateTime sentAt;

    /**
     * @PrePersist — A JPA lifecycle callback.
     * This method is automatically called by Hibernate just before
     * an entity is first saved to the database (INSERT).
     *
     * By setting sentAt here, we guarantee it's always populated,
     * even if the caller forgot to set it.
     */
    @PrePersist
    protected void onCreated() {
        if (sentAt == null) {
            sentAt = LocalDateTime.now();
        }
    }

    /**
     * MessageType — The type/purpose of a chat message.
     *
     * Using an enum (not magic strings like "join") gives us:
     *   - Compile-time safety (typos are caught at compile time)
     *   - IDE auto-completion
     *   - Exhaustive switch statements
     *
     * CHAT  — A normal message from a user ("Hello everyone!")
     * JOIN  — A system notification ("Alice joined the chat")
     * LEAVE — A system notification ("Bob left the chat")
     */
    public enum MessageType {
        CHAT,
        JOIN,
        LEAVE
    }
}
