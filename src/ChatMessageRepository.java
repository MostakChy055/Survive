package com.mostak.chatroom.service;

import com.mostak.chatroom.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ChatMessageRepository — The database access layer for ChatMessage.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHAT IS THE REPOSITORY PATTERN?
 *
 * It's a design pattern (from Domain-Driven Design) that hides
 * database details behind a clean interface.
 *
 * Your controller doesn't write SQL.
 * Your controller doesn't know about JDBC connections.
 * Your controller just calls: messageRepository.save(msg)
 * And the repository figures out the SQL.
 *
 * This separation means you can switch databases (H2 → PostgreSQL)
 * without changing a single line of controller code.
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * HOW DOES THIS INTERFACE WORK WITHOUT AN IMPLEMENTATION CLASS?
 *
 * You're looking at an interface with no implementation.
 * That seems wrong — how does it work?
 *
 * Spring Data JPA generates a proxy class at startup.
 * It reads this interface and creates a real class behind the scenes.
 * That generated class handles all the database communication.
 *
 * You never write an @Override or an impl class.
 * Spring does it for you.
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * JpaRepository<ChatMessage, Long>
 *   Generic parameters:
 *     ChatMessage → the entity type this repository manages
 *     Long        → the type of the @Id field (our id is Long)
 *
 * By extending JpaRepository, we get these methods FOR FREE:
 *   save(entity)            → INSERT or UPDATE
 *   findById(id)            → SELECT WHERE id = ?  → returns Optional<ChatMessage>
 *   findAll()               → SELECT * FROM chat_messages
 *   deleteById(id)          → DELETE WHERE id = ?
 *   count()                 → SELECT COUNT(*)
 *   existsById(id)          → SELECT 1 WHERE id = ?
 *   saveAll(list)           → batch INSERT
 *   ... and more
 *
 * @Repository
 *   Marks this as a Spring-managed component.
 *   Also enables Spring to translate database exceptions into
 *   Spring's DataAccessException hierarchy.
 *   Without this, a database error would throw a raw Hibernate exception.
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * findTop50ByOrderBySentAtAsc()
     *
     * Fetches the 50 oldest messages, sorted by sentAt ascending (oldest first).
     * Used when a user first loads the chatroom — to show recent history.
     *
     * ─────────────────────────────────────────────────────────────────────────
     * QUERY DERIVATION — How Spring reads this method name:
     *
     *   find         → SELECT
     *   Top50        → LIMIT 50
     *   By           → WHERE clause starts (no condition = no WHERE)
     *   OrderBy      → ORDER BY
     *   SentAt       → the sentAt field (maps to sent_at column)
     *   Asc          → ASC (ascending = oldest first)
     *
     * Generated SQL:
     *   SELECT * FROM chat_messages
     *   ORDER BY sent_at ASC
     *   LIMIT 50
     *
     * Spring Data JPA does this automatically — no @Query annotation needed.
     * ─────────────────────────────────────────────────────────────────────────
     *
     * WHY 50?
     *   Loading all messages would be slow as the chatroom grows.
     *   50 gives context without overloading the browser.
     *   In a production app, you'd use cursor-based pagination.
     *
     * WHY oldest first (Asc)?
     *   Chat reads top-to-bottom, oldest to newest.
     *   If we loaded newest-first, we'd have to reverse the list on the client.
     */
    List<ChatMessage> findTop50ByOrderBySentAtAsc();
}
