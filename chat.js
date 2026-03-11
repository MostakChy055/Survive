/**
 * stores/chat.js — Chat state management using Pinia.
 *
 * This store manages everything chat-related:
 *   - The list of messages (including history + real-time)
 *   - Connected users list
 *   - WebSocket connection status
 *   - Actions: connect, disconnect, send message, load history
 *
 * It acts as the bridge between:
 *   - The WebSocket service (transport layer)
 *   - The components (UI layer)
 *
 * Components never call websocketService directly —
 * they call chatStore actions, which internally use websocketService.
 * This keeps components simple and makes the store the single source of truth.
 */

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { websocketService } from '@/services/websocket'
import api from '@/services/api'
import dayjs from 'dayjs'

export const useChatStore = defineStore('chat', () => {

  // ─── STATE ────────────────────────────────────────────────────────────────

  /**
   * messages — The array of chat messages displayed in the UI.
   *
   * Each message object shape (matches ChatMessage.java):
   * {
   *   id: Long,
   *   content: String,
   *   sender: String,
   *   type: 'CHAT' | 'JOIN' | 'LEAVE',
   *   sentAt: String (ISO datetime)
   * }
   *
   * We populate this from:
   *   1. loadHistory() — fetches last 50 messages via REST on connect
   *   2. Real-time messages arriving via WebSocket subscription
   */
  const messages = ref([])

  /**
   * connectedUsers — Set of usernames currently in the chat.
   *
   * We track this by:
   *   - Adding a user when we see a JOIN message
   *   - Removing a user when we see a LEAVE message
   *
   * Using a Set (not an array) prevents duplicates.
   * ref(new Set()) — Vue reactivity works with Set in Vue 3.
   */
  const connectedUsers = ref(new Set())

  /**
   * connectionStatus — Current WebSocket connection state.
   * Values: 'disconnected' | 'connecting' | 'connected' | 'error'
   *
   * Used by the UI to show connection indicators.
   */
  const connectionStatus = ref('disconnected')

  // ─── GETTERS ──────────────────────────────────────────────────────────────

  /**
   * isConnected — True when WebSocket is connected and ready.
   */
  const isConnected = computed(() => connectionStatus.value === 'connected')

  /**
   * connectedUsersList — Array version of connectedUsers Set.
   *
   * Vue templates work better with arrays than Sets for iteration.
   * [...set] spreads the Set into an array.
   * computed() ensures this updates whenever connectedUsers changes.
   */
  const connectedUsersList = computed(() => [...connectedUsers.value])

  // ─── ACTIONS ──────────────────────────────────────────────────────────────

  /**
   * loadHistory() — Fetches the last 50 messages from the REST API.
   *
   * Called when the user first connects to the chatroom.
   * We load history BEFORE subscribing to real-time messages,
   * then incoming real-time messages are appended to the history.
   *
   * Note: messages from history and real-time may briefly overlap
   * (a message sent between history load and subscription start).
   * For production, use message IDs to deduplicate.
   */
  async function loadHistory() {
    try {
      const response = await api.get('/messages')
      messages.value = response.data

      /**
       * Rebuild connectedUsers from history.
       * We scan JOIN and LEAVE events to reconstruct who's currently in the chat.
       * This is approximate (JOIN/LEAVE order matters) but good enough for learning.
       */
      connectedUsers.value = new Set()
      response.data.forEach(msg => {
        if (msg.type === 'JOIN') connectedUsers.value.add(msg.sender)
        if (msg.type === 'LEAVE') connectedUsers.value.delete(msg.sender)
      })

    } catch (error) {
      console.error('Failed to load chat history:', error)
    }
  }

  /**
   * connectToChat() — Establishes WebSocket connection and subscribes.
   *
   * @param {string} username — The logged-in user's username.
   *
   * Steps:
   *   1. Load message history (REST)
   *   2. Connect via WebSocket + STOMP
   *   3. Subscribe to /topic/chatroom (broadcast)
   *   4. Send a JOIN notification to the server
   */
  async function connectToChat(username) {
    connectionStatus.value = 'connecting'

    try {
      // Step 1: Load history first so we have context before subscribing
      await loadHistory()

      // Step 2: Establish WebSocket connection
      await websocketService.connect(
        // onConnected callback
        () => {
          connectionStatus.value = 'connected'

          // Step 3: Subscribe to the chatroom topic
          /**
           * '/topic/chatroom' — The broadcast destination.
           * All messages sent to this topic by the server are delivered here.
           * The callback fires for every new message.
           */
          websocketService.subscribe('/topic/chatroom', (message) => {
            handleIncomingMessage(message)
          })

          // Step 4: Announce that this user has joined
          /**
           * We send to '/app/chat.addUser' (the @MessageMapping in ChatController).
           * The server stores our username in the session, saves a JOIN event,
           * and broadcasts the JOIN message back to /topic/chatroom.
           */
          websocketService.sendMessage('/app/chat.addUser', {
            sender: username,
            type: 'JOIN',
            content: ''
          })
        },

        // onError callback
        (error) => {
          console.error('WebSocket connection error:', error)
          connectionStatus.value = 'error'
        }
      )

    } catch (error) {
      console.error('Failed to connect to chat:', error)
      connectionStatus.value = 'error'
    }
  }

  /**
   * disconnectFromChat() — Cleanly disconnects from the chatroom.
   *
   * The server will detect the disconnect via SessionDisconnectEvent,
   * broadcast the LEAVE message, and save it to the DB.
   */
  function disconnectFromChat() {
    websocketService.disconnect()
    connectionStatus.value = 'disconnected'
    messages.value = []
    connectedUsers.value = new Set()
  }

  /**
   * sendChatMessage() — Sends a chat message to the server.
   *
   * @param {string} content — The message text.
   * @param {string} sender  — The sender's username.
   *
   * We send to '/app/chat.send' (ChatController.sendMessage()).
   * The server validates, saves, and broadcasts back to /topic/chatroom.
   * We DO NOT add the message to the local list here —
   * we wait for it to come back from the server (with id, sentAt).
   * This ensures all clients (including sender) show the same data.
   */
  function sendChatMessage(content, sender) {
    if (!content.trim()) return false

    const success = websocketService.sendMessage('/app/chat.send', {
      content: content.trim(),
      sender: sender,  // Server will override this with authenticated user
      type: 'CHAT'
    })

    return success
  }

  /**
   * handleIncomingMessage() — Processes a real-time message from the WebSocket.
   *
   * Called by the subscription callback for every message arriving on /topic/chatroom.
   * Updates the messages array and connectedUsers set accordingly.
   *
   * @param {Object} message — Parsed ChatMessage object from the server.
   */
  function handleIncomingMessage(message) {
    // Append the new message to the list
    messages.value.push(message)

    // Update connected users based on message type
    if (message.type === 'JOIN') {
      connectedUsers.value.add(message.sender)
    } else if (message.type === 'LEAVE') {
      connectedUsers.value.delete(message.sender)
    }

    /**
     * Auto-scroll to bottom behavior is handled in the component
     * (MessageList.vue) by watching the messages array.
     *
     * We don't scroll from the store — stores manage data, not DOM.
     * That's the component's job (separation of concerns).
     */
  }

  /**
   * formatTime() — Formats a message timestamp for display.
   *
   * @param {string} sentAt — ISO datetime string from the server.
   * @returns {string} — Human-readable time like "2:34 PM"
   *
   * We use dayjs for date formatting — it's a 2kb alternative to moment.js.
   * 'h:mm A' format = 12-hour time with AM/PM.
   */
  function formatTime(sentAt) {
    if (!sentAt) return ''
    return dayjs(sentAt).format('h:mm A')
  }

  // ─── EXPOSE ───────────────────────────────────────────────────────────────

  return {
    messages,
    connectedUsers,
    connectionStatus,
    isConnected,
    connectedUsersList,
    loadHistory,
    connectToChat,
    disconnectFromChat,
    sendChatMessage,
    formatTime
  }
})
