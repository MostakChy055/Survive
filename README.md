# ChatRoom — Spring Boot + Vue.js + WebSocket

A real-time chatroom using Spring Boot (STOMP/WebSocket in-memory broker) and Vue.js 3.

---

## Architecture Quick Reference

```
Vue.js (5173)  <──WebSocket──>  Spring Boot (8080)
     ↕ REST (via Vite proxy)           ↕
   Pinia stores               H2 in-memory DB
   websocket.js               Message broker (in-memory)
```

---

## Project Structure

```
chatroom/
├── backend/                        ← Spring Boot
│   ├── pom.xml
│   └── src/main/java/com/chatroom/
│       ├── ChatroomApplication.java        ← Entry point
│       ├── config/
│       │   ├── WebSocketConfig.java        ← STOMP broker setup
│       │   └── SecurityConfig.java         ← Auth + CORS
│       ├── model/
│       │   └── ChatMessage.java            ← Entity + DTO
│       ├── service/
│       │   └── ChatMessageRepository.java  ← JPA repository
│       └── controller/
│           ├── ChatController.java         ← @MessageMapping handlers
│           ├── WebSocketEventListener.java ← Connect/disconnect events
│           └── MessageRestController.java  ← REST: history + auth
│
└── frontend/                       ← Vue.js 3
    ├── package.json
    ├── vite.config.js              ← Dev proxy (API + WebSocket)
    ├── tailwind.config.js
    └── src/
        ├── main.js                 ← App bootstrap
        ├── App.vue                 ← Root component
        ├── router/index.js         ← Vue Router + auth guard
        ├── services/
        │   ├── api.js              ← Axios HTTP client
        │   └── websocket.js        ← STOMP client singleton
        ├── stores/
        │   ├── auth.js             ← Auth state (Pinia)
        │   └── chat.js             ← Chat state (Pinia)
        └── views/
            ├── LoginView.vue       ← Login page
            └── ChatView.vue        ← Main chatroom
```

---

## Setup & Run

### Prerequisites
- Java 17+
- Maven 3.8+
- Node.js 18+

### 1. Start the Spring Boot backend

```bash
cd backend
mvn spring-boot:run
```

Backend runs at: http://localhost:8080

Useful URLs while developing:
- H2 Console: http://localhost:8080/h2-console
  - JDBC URL: `jdbc:h2:mem:chatroom`
  - Username: `sa`  Password: (empty)

### 2. Start the Vue.js frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend runs at: http://localhost:5173

---

## Test Credentials

Three users are pre-configured in SecurityConfig.java:

| Username | Password |
|----------|----------|
| alice    | password |
| bob      | password |
| charlie  | password |

Open 2-3 browser tabs (or different browsers) and log in as different users to see real-time messaging.

---

## Message Flow (detailed)

```
1. User types "Hello" and presses Enter
2. Vue: chatStore.sendChatMessage("Hello", "alice")
3. Vue: websocketService.sendMessage('/app/chat.send', { content: "Hello", type: "CHAT" })
4. STOMP SEND frame → ws://localhost:8080/ws (via Vite proxy)
5. Spring: routes to ChatController.sendMessage() (strips "/app")
6. Spring: sets sender = "alice" (from authenticated Principal)
7. Spring: sets sentAt = LocalDateTime.now() (server time)
8. Spring: saves to H2 database via messageRepository.save()
9. Spring @SendTo: publishes to /topic/chatroom
10. In-memory broker: delivers to ALL subscribers of /topic/chatroom
11. Every connected Vue.js client: receives STOMP MESSAGE frame
12. chatStore.handleIncomingMessage(parsed): pushes to messages array
13. Vue reactivity: MessageList re-renders with new message
14. Auto-scroll to bottom via watcher + nextTick
```

---

## Key Concepts

**Why STOMP over raw WebSocket?**
Raw WebSocket is just a byte pipe. STOMP adds routing (destinations), headers, and pub/sub semantics — essential for a multi-user chat.

**Why Vite proxy instead of direct calls to :8080?**
Browser same-origin policy blocks CORS requests with credentials (session cookies). The proxy makes requests appear same-origin, bypassing this cleanly in development.

**Why is sender set server-side?**
A malicious client could send `{ sender: "admin", content: "..." }` to impersonate another user. Always trust the authenticated Principal, never the payload.

**Why load history via REST and not WebSocket?**
REST is request/response — perfect for one-time data loads. WebSocket is event-driven — perfect for streaming new events. Mixing them would complicate the protocol unnecessarily.
