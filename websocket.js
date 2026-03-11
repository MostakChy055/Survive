/**
 * services/websocket.js — WebSocket + STOMP client service.
 *
 * This is a singleton service (one instance for the entire app lifetime).
 * It encapsulates all WebSocket complexity:
 *   - Creating and configuring the STOMP client
 *   - Connecting and disconnecting
 *   - Managing subscriptions
 *   - Sending messages
 *   - Handling reconnections
 *
 * By isolating WebSocket logic here, our Vue components stay clean —
 * they just call websocketService.sendMessage() without knowing STOMP details.
 *
 * Separation of Concerns principle: components handle UI, services handle transport.
 */

import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

/**
 * createWebSocketService() — Factory function that creates the service.
 *
 * We use a factory (not a class) to better align with Vue 3's Composition API.
 * The returned object exposes only the methods components need — internal details
 * (like the STOMP client itself) are private to this closure.
 *
 * This is the "Module Pattern" — private state + public API.
 */
function createWebSocketService() {

  /**
   * Private state — not exposed to consumers.
   *
   * stompClient — The @stomp/stompjs Client instance.
   *   null until connect() is called.
   *
   * subscriptions — Map of destination → STOMP subscription object.
   *   Stored so we can unsubscribe later (e.g., when leaving a room).
   *   Key: destination string (e.g., "/topic/chatroom")
   *   Value: STOMP subscription object (has an .unsubscribe() method)
   */
  let stompClient = null
  const subscriptions = new Map()

  /**
   * connect() — Establishes the WebSocket + STOMP connection.
   *
   * @param {Function} onConnected  — called when STOMP CONNECTED frame received
   * @param {Function} onError      — called when connection fails
   * @returns {Promise}             — resolves when connected, rejects on error
   *
   * We return a Promise so callers can await the connection:
   *   await websocketService.connect(onMsg, onError)
   *   // connection is ready here, safe to subscribe
   */
  function connect(onConnected, onError) {
    return new Promise((resolve, reject) => {

      /**
       * new Client() — Creates the STOMP client.
       *
       * @stomp/stompjs's Client handles:
       *   - STOMP protocol framing (CONNECT, SEND, SUBSCRIBE, DISCONNECT)
       *   - Heartbeat negotiation (keeps connection alive)
       *   - Automatic reconnection
       *   - Message serialization/deserialization
       */
      stompClient = new Client({

        /**
         * webSocketFactory — How to create the underlying WebSocket connection.
         *
         * We use SockJS instead of native WebSocket for two reasons:
         *   1. SockJS falls back to long-polling if WebSocket is unavailable
         *      (some corporate networks block WebSocket upgrade)
         *   2. It matches the .withSockJS() config in Spring's WebSocketConfig
         *
         * '/ws' is proxied by Vite to http://localhost:8080/ws (see vite.config.js)
         * Spring Boot's SockJS endpoint is registered at /ws.
         */
        webSocketFactory: () => new SockJS('/ws'),

        /**
         * Heartbeat configuration.
         *
         * STOMP heartbeats keep the connection alive and detect dead connections.
         * The client and server negotiate: the actual interval is max(client, server).
         *
         * outgoing: 4000 — client sends a heartbeat every 4 seconds
         * incoming: 4000 — client expects server heartbeat every 4 seconds
         *
         * If no heartbeat arrives within the expected interval,
         * the client considers the connection dead and attempts reconnect.
         */
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,

        /**
         * reconnectDelay — Milliseconds to wait before attempting reconnect.
         *
         * 5000ms (5 seconds) is a reasonable default.
         * The client will keep retrying indefinitely with this fixed delay.
         *
         * For production, implement exponential backoff (1s, 2s, 4s, 8s...).
         * @stomp/stompjs doesn't do exponential backoff natively,
         * but you can implement it in onDisconnect/onWebSocketError.
         */
        reconnectDelay: 5000,

        /**
         * onConnect — Called when the STOMP CONNECTED frame is received.
         *
         * STOMP connection sequence:
         *   1. WebSocket handshake (HTTP upgrade)
         *   2. Client sends STOMP CONNECT frame
         *   3. Server validates and sends STOMP CONNECTED frame
         *   4. onConnect fires — safe to subscribe and send messages
         *
         * @param {StompFrame} frame — the CONNECTED frame from server
         */
        onConnect: (frame) => {
          console.log('STOMP connected:', frame)
          if (onConnected) onConnected(frame)
          resolve(stompClient)
        },

        /**
         * onStompError — Called when the server sends an ERROR frame.
         *
         * STOMP ERROR frames are sent for protocol-level errors:
         *   - Invalid CONNECT headers
         *   - Subscription to unauthorized destination
         *   - Message too large
         *
         * @param {StompFrame} frame — the ERROR frame from server
         */
        onStompError: (frame) => {
          console.error('STOMP error:', frame)
          if (onError) onError(frame)
          reject(frame)
        },

        /**
         * onWebSocketError — Called for transport-level errors.
         *
         * These are lower-level than STOMP errors:
         *   - Network connection refused
         *   - TLS handshake failure
         *   - Connection timeout
         *
         * @param {Event} event — the WebSocket error event
         */
        onWebSocketError: (event) => {
          console.error('WebSocket error:', event)
          if (onError) onError(event)
          reject(event)
        },

        /**
         * onDisconnect — Called when the STOMP connection is closed.
         *
         * @stomp/stompjs will automatically attempt reconnection
         * if reconnectDelay > 0 (which it is — we set it to 5000 above).
         * You can show a "Reconnecting..." UI here.
         */
        onDisconnect: () => {
          console.log('STOMP disconnected — will reconnect')
        }
      })

      /**
       * stompClient.activate() — Starts the connection process.
       *
       * This is async — it initiates the WebSocket + STOMP handshake.
       * onConnect fires when it's complete.
       */
      stompClient.activate()
    })
  }

  /**
   * disconnect() — Cleanly closes the STOMP connection.
   *
   * deactivate() sends a STOMP DISCONNECT frame, then closes the WebSocket.
   * This is a graceful shutdown — the server receives the DISCONNECT frame
   * and fires the SessionDisconnectEvent in our event listener.
   *
   * Proper disconnection (as opposed to just closing the WebSocket) is
   * important because it lets the server know immediately that you've left,
   * rather than waiting for the heartbeat timeout to detect a dead connection.
   */
  function disconnect() {
    if (stompClient) {
      // Unsubscribe from all active subscriptions before disconnecting
      subscriptions.forEach((sub) => sub.unsubscribe())
      subscriptions.clear()

      stompClient.deactivate()
      stompClient = null
    }
  }

  /**
   * subscribe() — Subscribes to a STOMP destination.
   *
   * @param {string}   destination — The STOMP topic to subscribe to.
   *                                 Example: "/topic/chatroom"
   * @param {Function} callback    — Called each time a message arrives.
   *                                 Receives the STOMP message frame.
   *
   * The STOMP SUBSCRIBE frame tells the broker:
   *   "I want to receive all messages sent to this destination."
   *
   * The server keeps this subscription open.
   * Whenever someone sends to /topic/chatroom, we receive a MESSAGE frame.
   *
   * We store the subscription in our Map so we can unsubscribe later.
   */
  function subscribe(destination, callback) {
    if (!stompClient || !stompClient.connected) {
      console.warn('Cannot subscribe: not connected')
      return null
    }

    /**
     * stompClient.subscribe(destination, callback)
     *
     * callback receives a STOMP Message object:
     *   message.body        — JSON string of the message
     *   message.headers     — STOMP headers (message-id, subscription, etc.)
     *   message.ack()       — call to acknowledge the message (if required)
     *
     * We parse message.body (JSON string → JavaScript object) before
     * passing it to the callback, so components receive plain JS objects.
     */
    const subscription = stompClient.subscribe(destination, (message) => {
      try {
        const parsed = JSON.parse(message.body)
        callback(parsed)
      } catch (e) {
        console.error('Failed to parse message:', message.body, e)
      }
    })

    subscriptions.set(destination, subscription)
    return subscription
  }

  /**
   * unsubscribe() — Unsubscribes from a STOMP destination.
   *
   * Sends a STOMP UNSUBSCRIBE frame to the server.
   * After this, messages to that destination will no longer be received.
   *
   * @param {string} destination — The destination to unsubscribe from.
   */
  function unsubscribe(destination) {
    const sub = subscriptions.get(destination)
    if (sub) {
      sub.unsubscribe()
      subscriptions.delete(destination)
    }
  }

  /**
   * sendMessage() — Sends a STOMP message to the server.
   *
   * @param {string} destination — The STOMP destination to send to.
   *                               Example: "/app/chat.send"
   *                               ("/app" prefix routes to @MessageMapping in ChatController)
   * @param {Object} body        — The message payload (JavaScript object, not JSON string).
   *
   * stompClient.publish() — Sends a STOMP SEND frame:
   *   SEND
   *   destination:/app/chat.send
   *   content-type:application/json
   *
   *   {"content":"Hello","type":"CHAT"}
   *
   * JSON.stringify(body) converts the JS object to a JSON string for the STOMP body.
   */
  function sendMessage(destination, body) {
    if (!stompClient || !stompClient.connected) {
      console.warn('Cannot send message: not connected')
      return false
    }

    stompClient.publish({
      destination,
      body: JSON.stringify(body)
    })

    return true
  }

  /**
   * isConnected() — Returns true if STOMP is currently connected.
   * Useful for showing connection status in the UI.
   */
  function isConnected() {
    return stompClient?.connected ?? false
  }

  // Public API — only expose what components need
  return {
    connect,
    disconnect,
    subscribe,
    unsubscribe,
    sendMessage,
    isConnected
  }
}

/**
 * Export a single shared instance (singleton pattern).
 *
 * All components that import this service share the SAME instance.
 * This ensures there's only one WebSocket connection per browser tab.
 *
 * If we exported the factory function instead, each component would
 * create its own connection — wasteful and problematic.
 */
export const websocketService = createWebSocketService()
