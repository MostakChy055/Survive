// ─────────────────────────────────────────────────────────────────
// main.js — The entry point of the entire Vue application.
//
// This file runs first when the browser loads the app.
// Its job is to create the Vue application and mount it to the DOM.
// Think of it as the "wiring" file — it connects all the pieces.
// ─────────────────────────────────────────────────────────────────

// createApp — Creates a new Vue application instance.
// We pass in App.vue as the root component.
// Every other component lives inside App.vue (directly or nested).
import { createApp } from 'vue'

// Pinia — Vue's official state management library.
// State = data that multiple components need to share.
// Example: the logged-in username needs to be known by
// the topbar, the message list, and the send button.
// Without Pinia, you'd pass it as a prop through every component.
// With Pinia, any component can just import the store and read it.
import { createPinia } from 'pinia'

// Vue Router — handles navigation between pages.
// Example: / shows the login page, /chat shows the chatroom.
// Without Vue Router, you'd have to manually show/hide components.
// With Vue Router, each URL maps to a component automatically.
import router from './router/index.js'

// App.vue — The root component.
// All other components are children of this one.
// Right now it just renders <RouterView /> which shows
// whichever component matches the current URL.
import App from './App.vue'

// The global CSS file.
// Contains: Tailwind directives (@tailwind base, components, utilities)
// and any global styles we want.
import './style.css'

// ─────────────────────────────────────────────────────────────────
// Wire everything together and mount to the DOM.
//
// createApp(App)   — create the app with App.vue as root
// .use(createPinia()) — register Pinia (enables stores everywhere)
// .use(router)     — register Vue Router (enables routing everywhere)
// .mount('#app')   — attach to the <div id="app"> in index.html
//
// After mount(), Vue takes control of #app and renders the UI.
// ─────────────────────────────────────────────────────────────────
createApp(App)
  .use(createPinia())
  .use(router)
  .mount('#app')
