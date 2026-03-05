<template>
  <div class="dz-page">

    <div class="dz-wrapper">

      <!-- ── Drop Zone ─────────────────────────────────────────────────── -->
      <div
        class="dz-box"
        :class="{ 'dz-box--active': isDragging }"
        @dragover.prevent="isDragging = true"
        @dragleave.prevent="isDragging = false"
        @drop.prevent="handleDrop"
        @click="triggerInput"
      >
        <div class="dz-box__inner">
          <!-- Upload icon -->
          <div class="dz-icon" :class="{ 'dz-icon--active': isDragging }">
            <svg width="28" height="28" viewBox="0 0 24 24" fill="none"
              stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/>
              <polyline points="17 8 12 3 7 8"/>
              <line x1="12" y1="3" x2="12" y2="15"/>
            </svg>
          </div>

          <p class="dz-label">
            {{ isDragging ? 'Release to upload' : 'Drag and drop files here' }}
          </p>
          <p class="dz-sub">
            Images, videos, audio, PDFs &amp; more
            <span class="dz-browse">or <u>browse</u></span>
          </p>
        </div>

        <input
          ref="fileInputRef"
          type="file"
          multiple
          class="dz-input"
          @change="handleInput"
        />
      </div>

      <!-- ── File List ──────────────────────────────────────────────────── -->
      <transition name="list-fade">
        <div class="dz-list" v-if="files.length > 0">

          <!-- List header -->
          <div class="dz-list__header">
            <span class="dz-list__title">
              {{ files.length }} file{{ files.length !== 1 ? 's' : '' }}
              <span class="dz-list__hint">sorted by last modified</span>
            </span>
            <button class="dz-clear-btn" @click="clearAll">Clear all</button>
          </div>

          <!-- File rows -->
          <transition-group name="row" tag="ul" class="dz-list__items">
            <li
              v-for="file in files"
              :key="file.id"
              class="dz-row"
              @click="openPreview(file)"
            >
              <!-- Thumbnail / icon -->
              <div class="dz-row__thumb">
                <img
                  v-if="file.previewUrl && file.category === 'image'"
                  :src="file.previewUrl"
                  :alt="file.name"
                  class="dz-row__img"
                />
                <video
                  v-else-if="file.previewUrl && file.category === 'video'"
                  :src="file.previewUrl"
                  class="dz-row__img"
                  muted
                />
                <div v-else class="dz-row__ext">
                  {{ getExt(file.name) }}
                </div>
              </div>

              <!-- Name + meta -->
              <div class="dz-row__info">
                <span class="dz-row__name" :title="file.name">{{ file.name }}</span>
                <span class="dz-row__meta">
                  {{ formatSize(file.size) }}
                  &middot;
                  {{ formatDate(file.lastModified) }}
                </span>
              </div>

              <!-- Category pill -->
              <span class="dz-row__pill">{{ file.category }}</span>

              <!-- Actions -->
              <div class="dz-row__actions" @click.stop>
                <button
                  class="dz-row__preview-btn"
                  @click="openPreview(file)"
                  title="Preview"
                >
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none"
                    stroke="currentColor" stroke-width="2" stroke-linecap="round">
                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                    <circle cx="12" cy="12" r="3"/>
                  </svg>
                </button>
                <button
                  class="dz-row__del-btn"
                  @click="confirmDelete(file)"
                  title="Delete"
                >
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none"
                    stroke="currentColor" stroke-width="2" stroke-linecap="round">
                    <polyline points="3 6 5 6 21 6"/>
                    <path d="M19 6l-1 14H6L5 6"/>
                    <path d="M10 11v6M14 11v6"/>
                    <path d="M9 6V4h6v2"/>
                  </svg>
                </button>
              </div>
            </li>
          </transition-group>

        </div>
      </transition>

    </div><!-- /dz-wrapper -->


    <!-- ══════════════════════════════════════════════════════════════════
         PREVIEW MODAL
         Standard: Vue 3 <Teleport to="body"> — renders outside component
         tree to avoid CSS stacking context issues (Vue 3 docs best practice).
         WAI-ARIA: role="dialog" aria-modal="true" recommended for production.
    ════════════════════════════════════════════════════════════════════ -->
    <Teleport to="body">
      <transition name="modal-fade">
        <div
          v-if="preview.open"
          class="modal-backdrop"
          @click.self="closePreview"
          role="dialog"
          aria-modal="true"
        >
          <div class="modal">

            <!-- Modal header -->
            <div class="modal__header">
              <div class="modal__title-row">
                <span class="modal__pill">{{ preview.file?.category }}</span>
                <p class="modal__name">{{ preview.file?.name }}</p>
              </div>
              <div class="modal__controls">
                <button class="modal__del-btn" @click="confirmDelete(preview.file)">
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none"
                    stroke="currentColor" stroke-width="2">
                    <polyline points="3 6 5 6 21 6"/>
                    <path d="M19 6l-1 14H6L5 6"/>
                    <path d="M10 11v6M14 11v6"/>
                    <path d="M9 6V4h6v2"/>
                  </svg>
                  Delete
                </button>
                <button class="modal__close" @click="closePreview">✕</button>
              </div>
            </div>

            <!-- Modal body — content varies by file category -->
            <div class="modal__body">

              <!--
                IMAGE: <img> with alt text
                Standard: WCAG 2.1 Success Criterion 1.1.1 — all images need text alternatives.
              -->
              <img
                v-if="preview.file?.category === 'image' && preview.file?.previewUrl"
                :src="preview.file.previewUrl"
                :alt="preview.file.name"
                class="modal__preview-img"
              />

              <!--
                VIDEO: HTML5 <video> with controls
                Standard: W3C HTML Living Standard — <video controls> is the
                accessible, standard way to embed video with browser-native UI.
                autoplay + muted = browser autoplay policy compliant (Chrome/Firefox policy).
              -->
              <video
                v-else-if="preview.file?.category === 'video' && preview.file?.previewUrl"
                :src="preview.file.previewUrl"
                controls autoplay muted
                class="modal__preview-video"
              ></video>

              <!--
                AUDIO: HTML5 <audio> with controls
                Standard: W3C HTML Living Standard.
              -->
              <div
                v-else-if="preview.file?.category === 'audio' && preview.file?.previewUrl"
                class="modal__preview-audio"
              >
                <div class="audio-visual">
                  <div class="audio-bars">
                    <span v-for="n in 12" :key="n" class="bar" :style="`--i:${n}`"></span>
                  </div>
                  <p class="audio-filename">{{ preview.file.name }}</p>
                </div>
                <audio :src="preview.file.previewUrl" controls class="audio-el"></audio>
              </div>

              <!--
                PDF: <iframe> embed
                Standard: Browsers natively render PDFs in <iframe>.
                object-type="application/pdf" can also be used but iframe is more compatible.
              -->
              <iframe
                v-else-if="preview.file?.category === 'pdf' && preview.file?.previewUrl"
                :src="preview.file.previewUrl"
                class="modal__preview-pdf"
                title="PDF preview"
              ></iframe>

              <!-- Generic — no binary preview available -->
              <div v-else class="modal__preview-generic">
                <div class="generic-icon">{{ getExt(preview.file?.name || '') }}</div>
                <p class="generic-msg">No preview available for this file type.</p>
                <p class="generic-type">{{ preview.file?.type || 'Unknown MIME type' }}</p>
              </div>

            </div>

            <!-- Modal footer — file metadata -->
            <div class="modal__footer">
              <span>Size: <b>{{ formatSize(preview.file?.size) }}</b></span>
              <span>Modified: <b>{{ formatDate(preview.file?.lastModified) }}</b></span>
              <span>Type: <b>{{ preview.file?.type || '—' }}</b></span>
            </div>

          </div>
        </div>
      </transition>
    </Teleport>


    <!-- ══════════════════════════════════════════════════════════════════
         DELETE CONFIRMATION DIALOG
         Standard: Nielsen Heuristic #5 — Error Prevention.
         Destructive actions must require a confirmation step.
    ════════════════════════════════════════════════════════════════════ -->
    <Teleport to="body">
      <transition name="modal-fade">
        <div
          v-if="del.open"
          class="modal-backdrop"
          @click.self="del.open = false"
        >
          <div class="confirm">
            <p class="confirm__title">Delete file?</p>
            <p class="confirm__name">{{ del.file?.name }}</p>
            <div class="confirm__btns">
              <button class="confirm__cancel" @click="del.open = false">Cancel</button>
              <button class="confirm__ok"     @click="executeDelete">Delete</button>
            </div>
          </div>
        </div>
      </transition>
    </Teleport>

  </div>
</template>


<script setup>
/**
 * DropZone.vue
 *
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  Standards applied                                              │
 * ├─────────────────────────┬───────────────────────────────────────┤
 * │  Vue 3 <script setup>   │ Composition API — Vue RFC 0013        │
 * │  <Teleport to="body">   │ Vue 3 modal best practice             │
 * │  HTML5 Drag & Drop API  │ W3C WHATWG spec                       │
 * │  URL.createObjectURL()  │ File API — W3C                        │
 * │  URL.revokeObjectURL()  │ Memory mgmt — MDN best practice       │
 * │  localStorage           │ Web Storage API — W3C (metadata only) │
 * │  crypto.randomUUID()    │ W3C Crypto API                        │
 * │  MIME categorisation    │ IANA media-types registry             │
 * │  <video controls>       │ W3C HTML Living Standard              │
 * │  <img alt="…">          │ WCAG 2.1 criterion 1.1.1              │
 * │  Confirm before delete  │ Nielsen Heuristic #5                  │
 * │  Sort DESC lastModified │ Standard UX: newest-first             │
 * │  CSS Custom Properties  │ W3C CSS Level 4 (design tokens)       │
 * │  CSS media queries      │ W3C Media Queries Level 4             │
 * └─────────────────────────┴───────────────────────────────────────┘
 *
 * NOTE — persistence model:
 *   localStorage  → stores file *metadata* (survives reload) ✅
 *   blobStore Map → stores File objects in memory only       ❌ (lost on reload)
 *   For full blob persistence across reloads → use IndexedDB
 *   (industry standard: Dexie.js library is the common wrapper).
 */

import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'

// ─── Constants ───────────────────────────────────────────────────────────────
const LS_KEY = 'dropzone_v3'

// ─── State ────────────────────────────────────────────────────────────────────
const isDragging   = ref(false)
const fileInputRef = ref(null)
const files        = ref([])          // Array of file metadata objects
const blobStore    = new Map()        // Map<id, File> — in-memory blob store

const preview = reactive({ open: false, file: null })
const del     = reactive({ open: false, file: null })

// ─── Lifecycle ────────────────────────────────────────────────────────────────
onMounted(loadFromStorage)

onBeforeUnmount(() => {
  // Revoke all ObjectURLs on teardown — MDN File API best practice
  files.value.forEach(f => f.previewUrl && URL.revokeObjectURL(f.previewUrl))
})

// ─── Persistence ─────────────────────────────────────────────────────────────
function saveToStorage() {
  // JSON cannot serialise binary blobs — store metadata only
  const meta = files.value.map(({ id, name, size, type, lastModified, category }) =>
    ({ id, name, size, type, lastModified, category })
  )
  localStorage.setItem(LS_KEY, JSON.stringify(meta))
}

function loadFromStorage() {
  try {
    const raw = localStorage.getItem(LS_KEY)
    if (raw) files.value = JSON.parse(raw).map(f => ({ ...f, previewUrl: null }))
  } catch { /* silent — corrupt storage */ }
}

// ─── File ingestion ───────────────────────────────────────────────────────────
const handleDrop  = e => { isDragging.value = false; ingest(Array.from(e.dataTransfer.files)) }
const handleInput = e => { ingest(Array.from(e.target.files)); e.target.value = '' }
const triggerInput = () => fileInputRef.value.click()

/**
 * IANA media-type based categorisation.
 * Reference: https://www.iana.org/assignments/media-types
 */
function categorise(mime = '') {
  if (mime.startsWith('image'))          return 'image'
  if (mime.startsWith('video'))          return 'video'
  if (mime.startsWith('audio'))          return 'audio'
  if (mime === 'application/pdf')        return 'pdf'
  return 'file'
}

function ingest(newFiles) {
  const seen = new Set(files.value.map(f => `${f.name}|${f.size}|${f.lastModified}`))

  const added = newFiles
    .filter(f => !seen.has(`${f.name}|${f.size}|${f.lastModified}`))
    .map(f => {
      const id       = crypto.randomUUID()          // W3C Crypto API
      const category = categorise(f.type)
      const previewUrl = ['image','video','audio','pdf'].includes(category)
        ? URL.createObjectURL(f)                    // W3C File API
        : null

      blobStore.set(id, f)

      return { id, name: f.name, size: f.size, type: f.type,
               lastModified: f.lastModified, category, previewUrl }
    })

  // Sort DESC by lastModified — industry UX standard (newest first)
  files.value = [...files.value, ...added]
    .sort((a, b) => b.lastModified - a.lastModified)

  saveToStorage()
}

// ─── Preview ──────────────────────────────────────────────────────────────────
function openPreview(file) {
  // Regenerate blob URL if missing but File still in memory
  if (!file.previewUrl && blobStore.has(file.id))
    file.previewUrl = URL.createObjectURL(blobStore.get(file.id))
  preview.file = file
  preview.open = true
}
const closePreview = () => { preview.open = false; preview.file = null }

// ─── Delete ───────────────────────────────────────────────────────────────────
function confirmDelete(file) { del.file = file; del.open = true }

function executeDelete() {
  const f = del.file; if (!f) return
  if (f.previewUrl) URL.revokeObjectURL(f.previewUrl)   // free memory
  blobStore.delete(f.id)
  files.value = files.value.filter(x => x.id !== f.id)
  saveToStorage()
  del.open = false; del.file = null
  if (preview.open && preview.file?.id === f.id) closePreview()
}

const clearAll = () => {
  files.value.forEach(f => f.previewUrl && URL.revokeObjectURL(f.previewUrl))
  blobStore.clear(); files.value = []
  localStorage.removeItem(LS_KEY)
}

// ─── Formatters ───────────────────────────────────────────────────────────────
const getExt      = name => (name.split('.').pop() || 'file').slice(0,5).toUpperCase()
const formatSize  = (b = 0) =>
  b < 1024 ? `${b} B` : b < 1024**2 ? `${(b/1024).toFixed(1)} KB` : `${(b/1024**2).toFixed(2)} MB`
const formatDate  = ts =>
  ts ? new Date(ts).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) : '—'
</script>


<style scoped>
/*
  Google Fonts — Geist for body, Geist Mono for meta text.
  Geist is Vercel's clean, modern typeface — widely used in dev tools.
*/
@import url('https://fonts.googleapis.com/css2?family=Geist:wght@300;400;500;600&family=Geist+Mono:wght@400;500&display=swap');

/* ──────────────────────────────────────────────────────────────────────────────
   CSS Design Tokens — W3C CSS Custom Properties (CSS Level 4)
   Industry standard: design tokens ensure consistent theming across components.
────────────────────────────────────────────────────────────────────────────── */
.dz-page {
  --white:        #ffffff;
  --bg:           #f7f7f8;
  --border:       #e2e2e6;
  --border-dash:  #d0d0d8;
  --border-hover: #b0b0bc;
  --border-active:#4f46e5;
  --text:         #18181b;
  --text-2:       #52525b;
  --text-3:       #a1a1aa;
  --accent:       #4f46e5;
  --accent-bg:    #eef2ff;
  --danger:       #ef4444;
  --danger-bg:    #fef2f2;
  --pill-bg:      #f4f4f5;
  --pill-text:    #71717a;
  --shadow-sm:    0 1px 3px rgba(0,0,0,.07), 0 1px 2px rgba(0,0,0,.05);
  --shadow-md:    0 4px 16px rgba(0,0,0,.08), 0 2px 6px rgba(0,0,0,.05);
  --shadow-lg:    0 20px 60px rgba(0,0,0,.14), 0 8px 20px rgba(0,0,0,.07);
  --radius:       12px;
  --radius-sm:    8px;
  --radius-xs:    6px;

  min-height: 100vh;
  background: var(--bg);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 60px 20px 80px;
  font-family: 'Geist', system-ui, sans-serif;
  color: var(--text);
  -webkit-font-smoothing: antialiased;
}

/* ── Wrapper card ── */
.dz-wrapper {
  width: 100%;
  max-width: 720px;
  background: var(--white);
  border: 1px solid var(--border);
  border-radius: 16px;
  padding: 28px;
  box-shadow: var(--shadow-sm);
}

/* ──────────────────────────────────────────────────────────────────────────────
   DROP ZONE BOX
   Matches the reference: white bg, dashed border, centered content, generous padding.
────────────────────────────────────────────────────────────────────────────── */
.dz-box {
  border: 1.5px dashed var(--border-dash);
  border-radius: var(--radius);
  background: var(--white);
  cursor: pointer;
  transition: border-color 0.2s ease, background 0.2s ease, box-shadow 0.2s ease;
  position: relative;
  overflow: hidden;
  user-select: none;
}

.dz-box:hover {
  border-color: var(--border-hover);
  background: #fafafa;
}

/* Active drag state */
.dz-box--active {
  border-color: var(--accent);
  border-style: solid;
  background: var(--accent-bg);
  box-shadow: 0 0 0 4px rgba(79,70,229,0.08);
}

.dz-box__inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 64px 32px;
  gap: 10px;
  text-align: center;
}

/* Upload icon */
.dz-icon {
  color: var(--text-3);
  margin-bottom: 4px;
  transition: color 0.2s, transform 0.2s;
}
.dz-icon--active {
  color: var(--accent);
  transform: translateY(-4px);
}

.dz-label {
  font-size: 15px;
  font-weight: 600;
  color: var(--text);
  margin: 0;
  letter-spacing: -0.01em;
}

.dz-sub {
  font-size: 13px;
  color: var(--text-3);
  margin: 0;
}

.dz-browse {
  color: var(--accent);
  cursor: pointer;
  margin-left: 2px;
}

.dz-input { display: none; }

/* ──────────────────────────────────────────────────────────────────────────────
   FILE LIST
────────────────────────────────────────────────────────────────────────────── */
.dz-list {
  margin-top: 20px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  overflow: hidden;
}

.dz-list__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 11px 16px;
  background: var(--bg);
  border-bottom: 1px solid var(--border);
}

.dz-list__title {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-2);
  display: flex;
  align-items: center;
  gap: 8px;
}

.dz-list__hint {
  font-family: 'Geist Mono', monospace;
  font-size: 10px;
  color: var(--text-3);
  font-weight: 400;
}

.dz-clear-btn {
  font-size: 11px;
  color: var(--danger);
  background: none;
  border: none;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 4px;
  transition: background 0.15s;
}
.dz-clear-btn:hover { background: var(--danger-bg); }

/* File list items */
.dz-list__items {
  list-style: none;
  margin: 0; padding: 0;
}

.dz-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border);
  cursor: pointer;
  transition: background 0.15s;
}
.dz-row:last-child { border-bottom: none; }
.dz-row:hover { background: #fafafa; }

/* Thumbnail */
.dz-row__thumb {
  width: 42px; height: 42px;
  border-radius: var(--radius-xs);
  overflow: hidden;
  flex-shrink: 0;
  border: 1px solid var(--border);
  background: var(--bg);
  display: flex; align-items: center; justify-content: center;
}
.dz-row__img {
  width: 100%; height: 100%;
  object-fit: cover;
}
.dz-row__ext {
  font-family: 'Geist Mono', monospace;
  font-size: 9px;
  font-weight: 500;
  color: var(--accent);
  letter-spacing: 0.05em;
  text-align: center;
  padding: 2px;
}

/* Name & meta */
.dz-row__info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.dz-row__name {
  font-size: 13px;
  font-weight: 500;
  color: var(--text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.dz-row__meta {
  font-family: 'Geist Mono', monospace;
  font-size: 10px;
  color: var(--text-3);
}

/* Category pill */
.dz-row__pill {
  font-size: 10px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.07em;
  padding: 2px 8px;
  border-radius: 99px;
  background: var(--pill-bg);
  color: var(--pill-text);
  border: 1px solid var(--border);
  flex-shrink: 0;
}

/* Row actions */
.dz-row__actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.15s;
}
.dz-row:hover .dz-row__actions { opacity: 1; }

.dz-row__preview-btn,
.dz-row__del-btn {
  width: 28px; height: 28px;
  border-radius: var(--radius-xs);
  border: 1px solid var(--border);
  background: var(--white);
  display: flex; align-items: center; justify-content: center;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s, color 0.15s;
  color: var(--text-3);
}
.dz-row__preview-btn:hover {
  background: var(--accent-bg);
  border-color: var(--accent);
  color: var(--accent);
}
.dz-row__del-btn:hover {
  background: var(--danger-bg);
  border-color: var(--danger);
  color: var(--danger);
}

/* ──────────────────────────────────────────────────────────────────────────────
   MODAL BACKDROP
   Standard: fixed overlay, high z-index, backdrop-filter blur (W3C CSS Backdrop Filter).
────────────────────────────────────────────────────────────────────────────── */
.modal-backdrop {
  position: fixed; inset: 0;
  background: rgba(0,0,0,0.35);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  z-index: 9999;
  display: flex; align-items: center; justify-content: center;
  padding: 20px;
}

/* ── Preview Modal ── */
.modal {
  background: var(--white);
  border: 1px solid var(--border);
  border-radius: 16px;
  width: 100%; max-width: 760px;
  max-height: 90vh;
  display: flex; flex-direction: column;
  overflow: hidden;
  box-shadow: var(--shadow-lg);
}

.modal__header {
  display: flex; align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
  gap: 12px;
}
.modal__title-row {
  display: flex; align-items: center;
  gap: 10px; min-width: 0;
}
.modal__pill {
  font-size: 10px; font-weight: 600;
  text-transform: uppercase; letter-spacing: 0.08em;
  padding: 2px 8px; border-radius: 99px;
  background: var(--accent-bg); color: var(--accent);
  border: 1px solid rgba(79,70,229,0.2);
  flex-shrink: 0;
}
.modal__name {
  font-size: 13px; font-weight: 500; color: var(--text);
  margin: 0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.modal__controls {
  display: flex; align-items: center; gap: 8px; flex-shrink: 0;
}
.modal__del-btn {
  display: flex; align-items: center; gap: 5px;
  font-family: 'Geist', sans-serif; font-size: 12px; font-weight: 500;
  color: var(--danger);
  background: var(--danger-bg);
  border: 1px solid rgba(239,68,68,0.2);
  border-radius: var(--radius-xs); padding: 6px 12px;
  cursor: pointer; transition: border-color 0.15s, background 0.15s;
}
.modal__del-btn:hover { border-color: var(--danger); background: #fee2e2; }
.modal__close {
  width: 30px; height: 30px;
  border-radius: var(--radius-xs);
  background: var(--bg); border: 1px solid var(--border);
  color: var(--text-3); font-size: 12px; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  transition: background 0.15s, color 0.15s;
}
.modal__close:hover { background: var(--border); color: var(--text); }

/* Modal body */
.modal__body {
  flex: 1; overflow: auto;
  display: flex; align-items: center; justify-content: center;
  background: var(--bg); min-height: 280px;
}
.modal__preview-img {
  max-width: 100%; max-height: 60vh;
  object-fit: contain; display: block;
}
.modal__preview-video {
  max-width: 100%; max-height: 60vh; display: block;
}
.modal__preview-audio {
  display: flex; flex-direction: column;
  align-items: center; gap: 20px;
  padding: 40px 24px; width: 100%;
}
.audio-visual {
  display: flex; flex-direction: column;
  align-items: center; gap: 14px;
}
.audio-bars {
  display: flex; align-items: flex-end; gap: 3px; height: 40px;
}
.bar {
  width: 3px;
  background: var(--accent);
  border-radius: 3px;
  animation: eq 1.2s ease-in-out infinite alternate;
  animation-delay: calc(var(--i) * 0.08s);
  height: 100%;
  opacity: 0.6;
}
@keyframes eq {
  from { transform: scaleY(0.2); }
  to   { transform: scaleY(1);   }
}
.audio-filename {
  font-size: 13px; color: var(--text-2); margin: 0;
}
.audio-el { width: 100%; max-width: 440px; }
.modal__preview-pdf {
  width: 100%; height: 62vh; border: none; display: block;
}
.modal__preview-generic {
  display: flex; flex-direction: column;
  align-items: center; gap: 10px; padding: 60px 24px;
}
.generic-icon {
  width: 72px; height: 72px; border-radius: 12px;
  border: 1px solid var(--border); background: var(--white);
  display: flex; align-items: center; justify-content: center;
  font-family: 'Geist Mono', monospace;
  font-size: 12px; font-weight: 600;
  color: var(--accent); letter-spacing: 0.06em;
  box-shadow: var(--shadow-sm);
}
.generic-msg  { font-size: 13px; color: var(--text-2); margin: 0; }
.generic-type { font-family: 'Geist Mono', monospace; font-size: 11px; color: var(--text-3); margin: 0; }

/* Modal footer */
.modal__footer {
  display: flex; gap: 16px; flex-wrap: wrap;
  padding: 11px 18px;
  border-top: 1px solid var(--border);
  font-family: 'Geist Mono', monospace;
  font-size: 10px; color: var(--text-3);
  flex-shrink: 0;
}
.modal__footer b { color: var(--text-2); font-weight: 500; }

/* ── Confirm dialog ── */
.confirm {
  background: var(--white);
  border: 1px solid var(--border);
  border-radius: 14px; padding: 28px;
  width: 100%; max-width: 340px;
  box-shadow: var(--shadow-lg);
}
.confirm__title {
  font-size: 16px; font-weight: 600;
  color: var(--text); margin: 0 0 6px;
}
.confirm__name {
  font-size: 12px; color: var(--text-3);
  margin: 0 0 24px; word-break: break-all;
}
.confirm__btns { display: flex; gap: 8px; }
.confirm__cancel {
  flex: 1; padding: 9px; border-radius: var(--radius-xs);
  background: var(--bg); border: 1px solid var(--border);
  font-family: 'Geist', sans-serif; font-size: 13px;
  color: var(--text-2); cursor: pointer;
  transition: background 0.15s;
}
.confirm__cancel:hover { background: var(--border); }
.confirm__ok {
  flex: 1; padding: 9px; border-radius: var(--radius-xs);
  background: var(--danger-bg);
  border: 1px solid rgba(239,68,68,0.25);
  font-family: 'Geist', sans-serif; font-size: 13px;
  color: var(--danger); cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}
.confirm__ok:hover { background: #fee2e2; border-color: var(--danger); }

/* ──────────────────────────────────────────────────────────────────────────────
   TRANSITIONS
   Standard: CSS transitions for UI state changes (W3C CSS Transitions Level 1).
────────────────────────────────────────────────────────────────────────────── */
.modal-fade-enter-active,
.modal-fade-leave-active { transition: opacity 0.18s ease; }
.modal-fade-enter-from,
.modal-fade-leave-to    { opacity: 0; }

.list-fade-enter-active { transition: opacity 0.2s ease, transform 0.2s ease; }
.list-fade-enter-from   { opacity: 0; transform: translateY(6px); }

.row-enter-active { transition: opacity 0.18s ease, transform 0.18s ease; }
.row-leave-active { transition: opacity 0.14s ease; }
.row-enter-from   { opacity: 0; transform: translateX(-8px); }
.row-leave-to     { opacity: 0; }

/* ──────────────────────────────────────────────────────────────────────────────
   RESPONSIVE — W3C Media Queries Level 4 (mobile-first approach)
────────────────────────────────────────────────────────────────────────────── */
@media (max-width: 580px) {
  .dz-box__inner { padding: 44px 20px; }
  .dz-row__pill  { display: none; }
  .modal         { border-radius: 16px 16px 0 0; align-self: flex-end; max-height: 92vh; }
}
</style>