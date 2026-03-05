const DB_NAME = "file-storage"
const STORE = "files"

export function initDB() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, 1)

    request.onupgradeneeded = (event) => {
      const db = event.target.result
      db.createObjectStore(STORE, { keyPath: "id", autoIncrement: true })
    }

    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
}

export async function saveFile(file) {
  const db = await initDB()
  const tx = db.transaction(STORE, "readwrite")
  const store = tx.objectStore(STORE)

  store.add({
    file,
    name: file.name,
    type: file.type,
    lastModified: file.lastModified
  })
}

export async function getFiles() {
  const db = await initDB()
  const tx = db.transaction(STORE, "readonly")
  const store = tx.objectStore(STORE)

  return new Promise((resolve) => {
    const request = store.getAll()
    request.onsuccess = () => resolve(request.result)
  })
}

export async function deleteFile(id) {
  const db = await initDB()
  const tx = db.transaction(STORE, "readwrite")
  const store = tx.objectStore(STORE)

  store.delete(id)
}