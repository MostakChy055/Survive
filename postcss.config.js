/**
 * PostCSS Configuration
 *
 * PostCSS is a CSS processing tool.
 * Tailwind CSS is actually a PostCSS plugin — it runs through
 * PostCSS to generate the final CSS output.
 *
 * tailwindcss  — generates utility classes from your Tailwind config
 * autoprefixer — adds vendor prefixes (-webkit-, -moz- etc.)
 *                so CSS works across all browsers automatically
 *
 * Vite reads this file automatically — you don't need to import it
 * anywhere. Just having it in the project root is enough.
 */
export default {
  plugins: {
    tailwindcss:  {},
    autoprefixer: {}
  }
}
