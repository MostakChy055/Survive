<template>
  <!--
    LoginView.vue â€” The login page (shown at URL: /)

    This is a PLACEHOLDER for now.
    Goal: confirm Vue is rendering, routing works, Tailwind works.
    We build the real login form in the next step.

    Notice we're using Tailwind utility classes directly on HTML elements.
    No separate CSS file needed for this component.
    All styling comes from the class names.
  -->
  <div class="min-h-screen flex items-center justify-center"
       style="background-color: var(--bg-base)">

    <div class="text-center">

      <!--
        Tailwind class breakdown:
        text-3xl     â†’ font-size: 1.875rem
        font-bold    â†’ font-weight: 700
        mb-2         â†’ margin-bottom: 0.5rem
        (the color comes from our CSS variable via inline style)
      -->
      <h1 class="text-3xl font-bold mb-2"
          style="color: var(--text-primary)">
        chatroom_
      </h1>

      <p class="text-sm mb-6 uppercase tracking-widest"
         style="color: var(--text-secondary)">
        vue 3 Â· vite Â· pinia Â· vue router
      </p>

      <!--
        This green badge confirms Vue is working.
        If you see this in the browser, the entire toolchain is set up:
          âœ“ Vite dev server running
          âœ“ Vue 3 rendering
          âœ“ Vue Router showing this component for URL /
          âœ“ Tailwind classes applying styles
      -->
      <div class="inline-block px-4 py-2 rounded-full text-xs uppercase tracking-widest mb-8"
           style="background: rgba(63,185,80,0.1); color: var(--green); border: 1px solid rgba(63,185,80,0.3)">
        âœ“ vue is working
      </div>

      <br/>

      <!--
        RouterLink â€” Vue Router's component for navigation.
        Renders as an <a> tag but uses client-side navigation.
        Clicking it changes the URL to /chat and renders ChatView.
        No page reload â€” instant.

        to="/chat" â€” the destination URL.
        We'll replace this with programmatic navigation
        (router.push('/chat')) after login is implemented.
      -->
      <RouterLink
        to="/chat"
        class="inline-block px-6 py-3 rounded-lg text-sm font-bold uppercase tracking-wider"
        style="background: var(--accent); color: white">
        go to chat â†’
      </RouterLink>

    </div>
  </div>
</template>

<script setup>
/**
 * No logic needed in this placeholder.
 * RouterLink is globally available because we registered
 * Vue Router in main.js with app.use(router).
 */
</script>
....

<template>
  <!--
    ChatView.vue â€” The chatroom page (shown at URL: /chat)

    Placeholder for now. We build the real chat UI in Step 3.
    Goal here: confirm routing works (/ â†’ LoginView, /chat â†’ ChatView).
  -->
  <div class="min-h-screen flex items-center justify-center"
       style="background-color: var(--bg-base)">

    <div class="text-center">

      <h1 class="text-3xl font-bold mb-2"
          style="color: var(--text-primary)">
        chat page
      </h1>

      <p class="text-sm mb-6 uppercase tracking-widest"
         style="color: var(--text-secondary)">
        this is where the chatroom will live
      </p>

      <div class="inline-block px-4 py-2 rounded-full text-xs uppercase tracking-widest mb-8"
           style="background: rgba(56,139,253,0.1); color: var(--accent); border: 1px solid rgba(56,139,253,0.3)">
        âœ“ routing works
      </div>

      <br/>

      <!-- Navigate back to login -->
      <RouterLink
        to="/"
        class="inline-block px-6 py-3 rounded-lg text-sm font-bold uppercase tracking-wider"
        style="background: var(--bg-raised); color: var(--text-secondary); border: 1px solid var(--border)">
        â† back to login
      </RouterLink>

    </div>
  </div>
</template>

<script setup>
</script>


.......
<template>
  <!--
    App.vue â€” The root component.

    <RouterView /> is a special Vue Router component.
    It acts as a placeholder that renders whichever component
    matches the current URL.

    When the URL is /:
      <RouterView /> renders <LoginView />

    When the URL is /chat:
      <RouterView /> renders <ChatView />

    This is the entire job of App.vue right now.
    As we add layouts, navigation, or global UI elements
    (like a toast notification system), they go here.
  -->
  <RouterView />
</template>

<script setup>
/**
 * <script setup> is Vue 3's Composition API shorthand.
 *
 * Anything you import or declare here is automatically
 * available in the <template> above â€” no need to
 * return anything like in the Options API.
 *
 * We don't need any logic in App.vue right now.
 * It's purely a layout shell.
 *
 * RouterView is auto-imported by Vue Router when you call
 * app.use(router) in main.js â€” no explicit import needed here.
 */
</script>
chat
