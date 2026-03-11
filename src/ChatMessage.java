package com.mostak.chatroom.model;

// ─────────────────────────────────────────────────────────────────────────────
// JPA IMPORTS — for database persistence
// ─────────────────────────────────────────────────────────────────────────────
import jakarta.persistence.*;

// ─────────────────────────────────────────────────────────────────────────────
// LOMBOK IMPORTS — for boilerplate elimination
// ─────────────────────────────────────────────────────────────────────────────
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ChatMessage — Represents one message in the chatroom.
 *
 * This class has TWO jobs simultaneously:
 *
 *   JOB 1 — DATABASE ENTITY
 *     The @Entity annotation tells Hibernate (the ORM) to create a
 *     "chat_messages" table and map each field to a column.
 *     When we call messageRepository.save(msg), Hibernate runs:
 *       INSERT INTO chat_messages (content, sender, type, sent_at) VALUES (?, ?, ?, ?)
 *
 *   JOB 2 — WEBSOCKET PAYLOAD
 *     When Spring sends this object via @SendTo("/topic/chatroom"),
 *     Jackson (the JSON library) automatically converts it to:
 *       { "id": 1, "content": "Hello", "sender": "alice", "type": "CHAT", "sentAt": "..." }
 *     That JSON string is what travels over the WebSocket to your browser.
 *     Your browser's onmessage receives it and JSON.parse() converts it back.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * LOMBOK ANNOTATIONS — what each one generates:
 *
 * @Data
 *   Generates ALL of:
 *     - getContent(), setContent(String content)  ← for every field
 *     - equals() and hashCode()                   ← for comparison
 *     - toString()                                ← for logging
 *   Without @Data, you'd write ~80 lines of boilerplate by hand.
 *
 * @Builder
 *   Generates the Builder pattern. Lets you write:
 *     ChatMessage msg = ChatMessage.builder()
 *         .type(MessageType.JOIN)
 *         .sender("alice")
 *         .content("alice joined")
 *         .sentAt(LocalDateTime.now())
 *         .build();
 *   Cleaner than new ChatMessage() + 5 setter calls.
 *
 * @NoArgsConstructor
 *   Generates: public ChatMessage() {}
 *   JPA REQUIRES a no-argument constructor to create entities.
 *   Jackson REQUIRES it to deserialize JSON into the object.
 *   Without this, both JPA and Jackson will throw errors.
 *
 * @AllArgsConstructor
 *   Generates: public ChatMessage(Long id, String content, ...)
 *   Required internally by @Builder (they work together).
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * @Entity
 *   Marks this as a JPA-managed entity.
 *   Hibernate creates a table for it and tracks instances.
 *
 * @Table(name = "chat_messages")
 *   Names the database table explicitly.
 *   Without this, Hibernate would name it "chat_message" (class name).
 *   Explicit naming = no surprises.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    /**
     * id — Primary key. Uniquely identifies each message.
     *
     * @Id
     *   Marks this as the primary key column.
     *
     * @GeneratedValue(strategy = GenerationType.IDENTITY)
     *   Tells Hibernate: "let the database auto-increment this."
     *   H2 generates: id BIGINT AUTO_INCREMENT PRIMARY KEY
     *   Value starts at 1 and increases by 1 for each INSERT.
     *
     *   We use Long (64-bit integer) not int (32-bit).
     *   int maxes out at ~2 billion. Long handles 9 quintillion.
     *   For a chatroom that could last years — Long is correct.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * content — The actual text of the message.
     *
     * @Column(nullable = false, length = 2000)
     *   nullable = false → NOT NULL constraint in the database.
     *                      A message without content is invalid.
     *   length = 2000    → VARCHAR(2000) column.
     *                      Matches the maxlength="2000" in Phase 1's HTML input.
     *                      If someone sends more than 2000 chars, the DB rejects it.
     */
    @Column(nullable = false, length = 2000)
    private String content;

    /**
     * sender — Who sent this message (the username).
     *
     * In Phase 5 (Security), we set this from the authenticated Principal,
     * not from what the client sends. This prevents impersonation.
     * In Phase 2, we trust what the client sends (simplified for learning).
     */
    @Column(nullable = false)
    private String sender;

    /**
     * type — What kind of message this is.
     *
     * @Enumerated(EnumType.STRING)
     *   Stores the enum value as a string in the database.
     *   The column contains "CHAT", "JOIN", or "LEAVE" — not 0, 1, 2.
     *
     *   WHY STRING over ORDINAL?
     *   @Enumerated(EnumType.ORDINAL) stores the index (0, 1, 2).
     *   If you ever reorder the enum values, all existing data becomes wrong.
     *   STRING is safer — "CHAT" means CHAT regardless of enum ordering.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageType type;

    /**
     * sentAt — Server timestamp of when the message was stored.
     *
     * We set this SERVER-SIDE in the controller.
     * WHY NOT trust the client's timestamp?
     *   - Client clocks can be wrong or manipulated
     *   - If two messages arrive simultaneously, server time ensures
     *     consistent ordering across all clients
     *   - A client could send a message claiming it was sent yesterday
     *
     * LocalDateTime = date + time, no timezone.
     * For production, use Instant (UTC) to handle multiple timezones.
     */
    @Column(nullable = false)
    private LocalDateTime sentAt;

    /**
     * @PrePersist — JPA lifecycle callback.
     *
     * Automatically called by Hibernate just before this entity
     * is saved to the database for the first time.
     *
     * We use it to guarantee sentAt is ALWAYS set.
     * Even if someone creates a ChatMessage without setting sentAt,
     * this method fills it in before the INSERT runs.
     *
     * This is a safety net — the controller also sets sentAt explicitly.
     */
    @PrePersist
    protected void onCreated() {
        if (sentAt == null) {
            sentAt = LocalDateTime.now();
        }
    }

    /**
     * MessageType — The three types of messages in our chatroom.
     *
     * Using an enum instead of a String field ("CHAT", "JOIN"...)
     * gives us compile-time safety — you cannot accidentally type "CHTA".
     *
     * CHAT  → A user sent a text message ("Hello everyone!")
     * JOIN  → A user connected ("alice joined the chat")
     * LEAVE → A user disconnected ("bob left the chat")
     *
     * This matches exactly what Phase 1's JavaScript sends:
     *   { type: 'CHAT', sender: 'alice', content: 'Hello' }
     * Jackson deserializes "CHAT" → MessageType.CHAT automatically.
     */
    public enum MessageType {
        CHAT,
        JOIN,
        LEAVE
    }
}
