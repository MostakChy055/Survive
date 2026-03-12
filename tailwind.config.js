/** @type {import('tailwindcss').Config} */

/**
 * Tailwind Configuration
 *
 * content: tells Tailwind WHERE to look for class names.
 * Tailwind scans these files and only includes CSS for classes
 * it actually finds. This keeps the final CSS bundle tiny.
 *
 * "./index.html"         — the root HTML file
 * "./src/**\/*.{vue,js}" — all Vue components and JS files
 *
 * If you add a new folder with Vue files later, add it here.
 */
export default {
  content: [
    "./index.html",
    "./src/**/*.{vue,js}"
  ],
  theme: {
    extend: {}
  },
  plugins: []
}
