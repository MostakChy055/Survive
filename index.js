// ─────────────────────────────────────────────────────────────────
// router/index.js — Defines the URL routes for the application.
//
// Vue Router maps URLs → Components.
// When the browser URL changes, Vue Router swaps out the component
// rendered inside <RouterView /> in App.vue.
// ─────────────────────────────────────────────────────────────────
import { createRouter, createWebHistory } from 'vue-router'

// The two pages (views) in our app.
// We import them here so the router knows which component to render
// when the user navigates to each URL.
import LoginView from '../views/LoginView.vue'
import ChatView  from '../views/ChatView.vue'

/**
 * routes — Array of route definitions.
 * Each object defines one URL and which component it shows.
 *
 * path:      the URL path (e.g. '/' or '/chat')
 * name:      a label — used to navigate by name instead of hardcoded path
 *            e.g. router.push({ name: 'chat' }) instead of router.push('/chat')
 * component: the Vue component to render at this path
 */
const routes = [
  {
    path:      '/',
    name:      'login',
    component: LoginView
  },
  {
    path:      '/chat',
    name:      'chat',
    component: ChatView
  }
]

/**
 * createRouter() — Creates the router instance.
 *
 * history: createWebHistory()
 *   Uses the HTML5 History API for clean URLs.
 *   URLs look like: /chat  (not /#/chat)
 *   The alternative, createWebHashHistory(), uses /#/chat
 *   which works without a server but looks less clean.
 *
 * routes: the array we defined above
 */
const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
