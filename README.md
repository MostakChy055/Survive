Perfect. Let's do this properly. Since you already have RabbitMQ installed, we just need to configure it and wire up the backend.

---

## Step 1 — Verify RabbitMQ is running

Before anything else, open your browser and go to:

```
http://localhost:15672
```

This is the RabbitMQ Management Console. Default credentials:
- Username: `guest`
- Password: `guest`

If you see the dashboard, RabbitMQ is running. If you can't reach it, open your terminal and run:

```bash
rabbitmq-service start
```

Or if you installed it via the installer on Windows, search for **RabbitMQ Service** in Windows Services and make sure it's running.

---

## Step 2 — Understand what we're building

Before code, understand the exact flow:

```
Browser sends message
        ↓
Spring Boot receives it (@MessageMapping)
        ↓
Spring Boot publishes to RabbitMQ Exchange
        ↓
RabbitMQ routes it to a Queue
        ↓
(We'll consume from that queue in the next step)
```

RabbitMQ has three concepts you need to know:

**Exchange** — The entry point. Messages are sent TO an exchange, not directly to a queue. Think of it as a post office sorting room. We will use a `direct` exchange called `chat.exchange`.

**Queue** — Where messages actually sit and wait. Think of it as a mailbox. We'll create a queue called `chat.queue`.

**Binding** — The rule that connects an exchange to a queue. "Messages arriving at `chat.exchange` with routing key `chat.message` go into `chat.queue`." Without a binding, messages arrive at the exchange and disappear.

So our setup:

```
Spring Boot → chat.exchange (direct) → [binding: chat.message] → chat.queue
```

---

## Step 3 — Add the dependency to `pom.xml`

Open your `pom.xml` and add this inside `<dependencies>`:

```xml
<!--
    spring-boot-starter-amqp
    
    AMQP = Advanced Message Queuing Protocol.
    RabbitMQ implements AMQP.
    
    This dependency gives us:
      - RabbitTemplate  → for publishing messages TO RabbitMQ
      - @RabbitListener → for consuming messages FROM RabbitMQ (later)
      - Auto-configuration of the connection to RabbitMQ
      - Jackson message converter (JSON serialization)
    
    Without this, Spring Boot has no idea RabbitMQ exists.
-->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

After adding it, click **Load Maven Changes** in IntelliJ (the banner that appears, or the Maven tab → reload button).

---

## Step 4 — Update `application.properties`

Add these lines to your existing `application.properties`:

```properties
# ─────────────────────────────
# RABBITMQ CONNECTION
# ─────────────────────────────

# RabbitMQ runs on localhost by default.
# Port 5672 is the AMQP port (not 15672 — that's the management console).
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672

# Default credentials that RabbitMQ ships with.
# In production you would create a dedicated user and change these.
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest

# Virtual host — RabbitMQ supports multiple isolated environments
# on one server. "/" is the default virtual host.
spring.rabbitmq.virtual-host=/

# ─────────────────────────────
# OUR CHAT CONFIGURATION
# These are custom properties we define ourselves.
# We reference them in RabbitMQConfig.java using @Value.
# Keeping names in properties (not hardcoded in Java) means
# changing them later requires no code changes.
# ─────────────────────────────

# The exchange — entry point for all chat messages
chat.exchange=chat.exchange

# The queue — where messages are stored until consumed
chat.queue=chat.queue

# The routing key — the rule that binds exchange to queue
chat.routing-key=chat.message
```

---

## Step 5 — Create `RabbitMQConfig.java`

Create this file inside your `config` package (`com.mostak.chatroom.config`):

```java
package com.mostak.chatroom.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQConfig — Declares all RabbitMQ infrastructure as Spring Beans.
 *
 * When Spring Boot starts, it reads this class and creates the
 * Exchange, Queue, and Binding in RabbitMQ automatically.
 * You do NOT need to create them manually in the RabbitMQ console.
 * Spring will create them if they don't exist, and leave them alone
 * if they already exist.
 *
 * @Configuration — Marks this as a source of @Bean definitions.
 * Spring reads every @Bean method in this class at startup.
 */
@Configuration
public class RabbitMQConfig {

    /**
     * @Value injects values from application.properties.
     *
     * @Value("${chat.exchange}") reads the property named "chat.exchange"
     * and assigns its value ("chat.exchange") to this field.
     *
     * WHY use @Value instead of hardcoding the string?
     * If you ever need to rename the exchange, you change one line
     * in application.properties — not every Java file that uses it.
     */
    @Value("${chat.exchange}")
    private String exchange;

    @Value("${chat.queue}")
    private String queue;

    @Value("${chat.routing-key}")
    private String routingKey;


    /**
     * queue() — Declares the queue in RabbitMQ.
     *
     * A Queue is where messages actually sit and wait.
     * Think of it as a mailbox. Messages land here and stay
     * until something (a consumer) reads and acknowledges them.
     *
     * new Queue(name, durable):
     *
     *   name    — "chat.queue" — the queue's identifier.
     *             Used by bindings to route messages here,
     *             and by consumers to read from here.
     *
     *   durable — true — the queue SURVIVES a RabbitMQ restart.
     *             If durable=false, the queue disappears when
     *             RabbitMQ restarts and all unread messages are lost.
     *             Always use true in production.
     *
     * @Bean — Spring registers this Queue object and also tells
     * the AMQP infrastructure to declare it in RabbitMQ on startup.
     */
    @Bean
    public Queue chatQueue() {
        return new Queue(queue, true);
    }


    /**
     * exchange() — Declares the exchange in RabbitMQ.
     *
     * An Exchange is the entry point — messages are published TO
     * an exchange, never directly to a queue.
     * The exchange then routes messages to queues based on rules (bindings).
     *
     * We use DirectExchange — the simplest routing type.
     * A direct exchange routes a message to a queue whose binding key
     * exactly matches the message's routing key.
     *
     * Example:
     *   Message arrives with routing key "chat.message"
     *   DirectExchange looks for a binding with key "chat.message"
     *   Finds our binding → delivers to chat.queue
     *
     * Other exchange types (not used here):
     *   FanoutExchange  — sends to ALL bound queues (ignores routing key)
     *   TopicExchange   — routing key can use wildcards (* and #)
     *   HeadersExchange — routes based on message headers, not routing key
     *
     * new DirectExchange(name, durable, autoDelete):
     *   durable    — true  — survives RabbitMQ restart
     *   autoDelete — false — don't delete when no queues are bound to it
     */
    @Bean
    public DirectExchange chatExchange() {
        return new DirectExchange(exchange, true, false);
    }


    /**
     * binding() — Creates the rule connecting exchange to queue.
     *
     * Without a binding, messages arrive at the exchange and are
     * silently discarded. The binding tells RabbitMQ:
     * "Messages arriving at chat.exchange with routing key chat.message
     *  should be delivered to chat.queue."
     *
     * BindingBuilder.bind(queue).to(exchange).with(routingKey)
     * reads almost like English:
     *   "Bind the queue TO the exchange WITH this routing key."
     *
     * Spring creates this binding in RabbitMQ on startup.
     */
    @Bean
    public Binding binding(Queue chatQueue, DirectExchange chatExchange) {
        return BindingBuilder
            .bind(chatQueue)
            .to(chatExchange)
            .with(routingKey);
    }


    /**
     * messageConverter() — Configures JSON serialization for messages.
     *
     * By default, RabbitTemplate sends messages as plain bytes or strings.
     * Jackson2JsonMessageConverter tells Spring to:
     *   - Serialize Java objects → JSON before sending to RabbitMQ
     *   - Deserialize JSON → Java objects when receiving from RabbitMQ
     *
     * Without this, you'd have to manually call JSON.stringify yourself
     * before every publish. With this, you just pass a ChatMessage object
     * and Spring handles the conversion.
     *
     * Jackson is the same JSON library Spring Boot uses everywhere else
     * (REST responses, WebSocket payloads etc.) — consistent behavior.
     */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }


    /**
     * rabbitTemplate() — The main tool for publishing messages to RabbitMQ.
     *
     * RabbitTemplate is Spring's high-level AMQP client.
     * It wraps the low-level AMQP connection details and gives you
     * a clean API:
     *
     *   rabbitTemplate.convertAndSend(exchange, routingKey, message)
     *
     * That one method:
     *   1. Serializes the message to JSON (via our messageConverter)
     *   2. Creates an AMQP message with the JSON as the body
     *   3. Publishes it to the specified exchange with the routing key
     *   4. RabbitMQ receives it, routes it to chat.queue
     *
     * ConnectionFactory — Spring auto-creates this from your
     * application.properties (host, port, username, password).
     * We inject it here and give it to RabbitTemplate.
     *
     * setMessageConverter — Wires in our Jackson converter so
     * RabbitTemplate uses JSON automatically.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter());
        return rabbitTemplate;
    }
}
```

---

## Step 6 — Create `ChatMessageProducer.java`

Create this in a new `service` package. This is the class that actually publishes to RabbitMQ:

```java
package com.mostak.chatroom.service;

import com.mostak.chatroom.model.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * ChatMessageProducer — Publishes chat messages to RabbitMQ.
 *
 * This class has ONE job: take a ChatMessage and send it to RabbitMQ.
 * It knows nothing about WebSocket, HTTP, or the database.
 * That separation is intentional — each class does one thing.
 *
 * Called from: ChatController (after saving message to database)
 *
 * @Service — Marks this as a Spring-managed service bean.
 * Functionally the same as @Component but communicates intent:
 * this class contains business logic, not web handling or data access.
 *
 * @Slf4j (Lombok) — Generates a logger field automatically:
 *   private static final Logger log = LoggerFactory.getLogger(ChatMessageProducer.class);
 * We use it to log what we're sending — useful for debugging.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatMessageProducer {

    /**
     * RabbitTemplate — injected by Spring.
     * This is the tool that actually talks to RabbitMQ.
     * We configured it in RabbitMQConfig with the JSON converter.
     */
    private final RabbitTemplate rabbitTemplate;

    /**
     * Injected from application.properties.
     * Same values we defined there — no hardcoding here.
     */
    @Value("${chat.exchange}")
    private String exchange;

    @Value("${chat.routing-key}")
    private String routingKey;


    /**
     * publishMessage() — Sends a ChatMessage to RabbitMQ.
     *
     * @param chatMessage — The fully populated ChatMessage to publish.
     *                      By the time this is called, the message
     *                      already has: id, content, sender, type, sentAt.
     *                      (Set by ChatController before calling this)
     *
     * What happens internally:
     *
     *   rabbitTemplate.convertAndSend(exchange, routingKey, chatMessage)
     *
     *   Step 1 — Jackson serializes chatMessage to JSON:
     *     {
     *       "id": 1,
     *       "content": "Hello",
     *       "sender": "alice",
     *       "type": "CHAT",
     *       "sentAt": "2024-01-15T14:32:00"
     *     }
     *
     *   Step 2 — RabbitTemplate wraps it in an AMQP Message object
     *     with headers: content-type=application/json, etc.
     *
     *   Step 3 — RabbitTemplate sends it to "chat.exchange"
     *     with routing key "chat.message"
     *
     *   Step 4 — RabbitMQ receives it at chat.exchange,
     *     matches routing key "chat.message" to our binding,
     *     delivers it to "chat.queue"
     *
     *   Step 5 — Message sits in chat.queue waiting for a consumer
     *     (we build the consumer in the next step)
     */
    public void publishMessage(ChatMessage chatMessage) {

        log.info(
            "Publishing message to RabbitMQ — exchange: {}, routingKey: {}, sender: {}, type: {}",
            exchange,
            routingKey,
            chatMessage.getSender(),
            chatMessage.getType()
        );

        rabbitTemplate.convertAndSend(exchange, routingKey, chatMessage);

        log.info("Message published successfully — id: {}", chatMessage.getId());
    }
}
```

---

## Step 7 — Update `ChatController.java`

Now wire `ChatMessageProducer` into the controller. The controller saves to DB first, then publishes to RabbitMQ:

```java
package com.mostak.chatroom.controller;

import com.mostak.chatroom.model.ChatMessage;
import com.mostak.chatroom.service.ChatMessageProducer;
import com.mostak.chatroom.service.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatMessageRepository messageRepository;
    private final ChatMessageProducer chatMessageProducer;  // ← NEW

    @MessageMapping("/chat.send")
    @SendTo("/topic/chatroom")
    public ChatMessage sendMessage(@Payload ChatMessage chatMessage) {

        // Step 1 — Set server-controlled fields
        chatMessage.setSentAt(LocalDateTime.now());
        chatMessage.setType(ChatMessage.MessageType.CHAT);

        // Step 2 — Save to database (permanent record)
        ChatMessage savedMessage = messageRepository.save(chatMessage);

        // Step 3 — Publish to RabbitMQ (NEW)
        // After DB save so savedMessage has the generated id.
        // RabbitMQ receives the full message with id populated.
        chatMessageProducer.publishMessage(savedMessage);

        // Step 4 — Return to broadcast via WebSocket (unchanged)
        return savedMessage;
    }

    @MessageMapping("/chat.addUser")
    @SendTo("/topic/chatroom")
    public ChatMessage addUser(
        @Payload ChatMessage chatMessage,
        SimpMessageHeaderAccessor headerAccessor
    ) {
        String sender = chatMessage.getSender();
        headerAccessor.getSessionAttributes().put("username", sender);

        ChatMessage joinMessage = ChatMessage.builder()
            .type(ChatMessage.MessageType.JOIN)
            .sender(sender)
            .content(sender + " joined the chat")
            .sentAt(LocalDateTime.now())
            .build();

        // Save JOIN event to DB
        ChatMessage savedJoin = messageRepository.save(joinMessage);

        // Also publish JOIN events to RabbitMQ — useful for analytics,
        // presence tracking, notifications etc. downstream
        chatMessageProducer.publishMessage(savedJoin);

        return savedJoin;
    }
}
```

---

## Step 8 — Verify it's working in the RabbitMQ Console

Start Spring Boot. Then:

1. Go to `http://localhost:15672`
2. Click the **Exchanges** tab — you should see `chat.exchange` in the list
3. Click the **Queues** tab — you should see `chat.queue` in the list
4. Open your Phase 2 HTML, connect, send a message
5. Go back to the Queues tab — click `chat.queue` — you'll see **Messages Ready** count go up

That number going up means messages are landing in RabbitMQ successfully.

---

## What the flow looks like now

```
Browser sends "Hello"
        ↓
ChatController.sendMessage()
        ↓
    ┌───┴───┐
    │       │
  Save    Publish to RabbitMQ
  to H2   chat.exchange → chat.queue
    │       │
    └───┬───┘
        ↓
  Return message
  @SendTo /topic/chatroom
  (WebSocket broadcast still works)
```

RabbitMQ is now receiving every chat message. The queue holds them. In the next step we'll build the consumer that reads from the queue and does something with them (broadcast to all connected clients across multiple server instances).

Let me know once you can see messages appearing in the RabbitMQ queue and we'll move on.
