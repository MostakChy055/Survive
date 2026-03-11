package com.chatroom.service;

import com.chatroom.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ChatMessageRepository — The data access layer for ChatMessage entities.
 *
 * This is Spring Data JPA's "Repository Pattern" (from Domain-Driven Design).
 *
 * By extending JpaRepository<ChatMessage, Long>, we get a full set of
 * database operations FOR FREE — no SQL needed:
 *
 *   save(entity)              → INSERT or UPDATE
 *   findById(id)              → SELECT WHERE id = ?
 *   findAll()                 → SELECT all rows
 *   deleteById(id)            → DELETE WHERE id = ?
 *   count()                   → SELECT COUNT(*)
 *   existsById(id)            → SELECT 1 WHERE id = ?
 *   ... and many more
 *
 * The two type parameters are:
 *   ChatMessage — the entity type this repository manages
 *   Long        — the type of the primary key (@Id field in ChatMessage)
 *
 * @Repository — Marks this as a Spring-managed component.
 *   Spring Data JPA will generate a proxy implementation at startup.
 *   You never write an @Override or implementation class.
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * findTop50ByOrderBySentAtAsc() — Fetch the 50 most recent messages, oldest first.
     *
     * This method is automatically implemented by Spring Data JPA using
     * "Query Derivation" — Spring reads the method name and writes the SQL:
     *
     *   SELECT * FROM chat_messages
     *   ORDER BY sent_at ASC
     *   LIMIT 50
     *
     * Naming convention breakdown:
     *   findTop50  → LIMIT 50
     *   By         → WHERE clause start (no condition here = all rows)
     *   OrderBy    → ORDER BY
     *   SentAt     → the sentAt field
     *   Asc        → ASC (ascending = oldest first)
     *
     * We load history in ascending order so the UI can display messages
     * from top (oldest) to bottom (newest) — the natural reading order.
     *
     * 50 is a sensible default for "recent history" on page load.
     * For production, you'd use Pageable for cursor-based pagination.
     */
    List<ChatMessage> findTop50ByOrderBySentAtAsc();
}
