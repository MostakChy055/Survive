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
