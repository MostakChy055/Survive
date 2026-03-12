import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * Vite Configuration
 *
 * Vite is our development server and build tool.
 * It serves the Vue frontend on http://localhost:5173.
 *
 * ─────────────────────────────────────────────────────────────────
 * THE PROXY — THE MOST IMPORTANT PART OF THIS FILE
 *
 * Our Vue app runs on:   http://localhost:5173  (Vite dev server)
 * Spring Boot runs on:   http://localhost:8080  (embedded Tomcat)
 *
 * These are DIFFERENT ORIGINS (different ports = different origin).
 * Browsers enforce the Same-Origin Policy:
 *   A page on localhost:5173 CANNOT make requests to localhost:8080
 *   without CORS headers OR a proxy.
 *
 * The proxy solves this cleanly:
 *   Instead of the browser directly hitting localhost:8080,
 *   it hits localhost:5173 (same origin as the page).
 *   Vite intercepts matching requests and FORWARDS them to localhost:8080.
 *   The browser never sees a cross-origin request — no CORS issue.
 *
 * Rule 1: Any request to /api/* is forwarded to Spring Boot
 *   Vue code calls:        fetch('/api/messages')
 *   Browser sends to:      http://localhost:5173/api/messages
 *   Vite forwards to:      http://localhost:8080/api/messages
 *   Spring Boot responds:  [...messages...]
 *   Vite returns to Vue:   [...messages...]
 *
 * Rule 2: Any request to /ws/* is forwarded to Spring Boot
 *   This handles the WebSocket/SockJS connection.
 *   SockJS needs ws:true because WebSocket uses a different protocol.
 *
 * changeOrigin: true
 *   Rewrites the Host header of the forwarded request to match
 *   the target. Without this, Spring Boot might reject the request
 *   because the Host header says "localhost:5173" not "localhost:8080".
 * ─────────────────────────────────────────────────────────────────
 */
export default defineConfig({
  plugins: [vue()],

  server: {
    port: 5173,

    proxy: {
      '/api': {
        target:       'http://localhost:8080',
        changeOrigin: true
      },

      '/ws': {
        target:       'http://localhost:8080',
        changeOrigin: true,
        ws:           true
      }
    }
  }
})
