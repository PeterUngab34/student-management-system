// Persists the SQLite file bytes in IndexedDB, falling back to localStorage (base64) when
// IndexedDB is unavailable (private mode, blocked storage). Every access is guarded so the app
// still runs with an in-memory database when nothing can be stored.

const DB_NAME = 'sms-web';
const STORE = 'files';
const KEY = 'database';
const LS_KEY = 'sms-web-database';

function idb() {
  return new Promise((resolve, reject) => {
    if (typeof indexedDB === 'undefined') return reject(new Error('IndexedDB unavailable'));
    const req = indexedDB.open(DB_NAME, 1);
    req.onupgradeneeded = () => req.result.createObjectStore(STORE);
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
    req.onblocked = () => reject(new Error('IndexedDB blocked'));
  });
}

function tx(db, mode, fn) {
  return new Promise((resolve, reject) => {
    const t = db.transaction(STORE, mode);
    const req = fn(t.objectStore(STORE));
    t.oncomplete = () => resolve(req && req.result);
    t.onerror = () => reject(t.error);
    t.onabort = () => reject(t.error);
  });
}

function toBase64(bytes) {
  let s = '';
  for (let i = 0; i < bytes.length; i += 0x8000) s += String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000));
  return btoa(s);
}

function fromBase64(s) {
  const bin = atob(s);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return bytes;
}

export const storage = {
  /** 'indexeddb' | 'localstorage' | 'memory' - set by the first successful call. */
  mode: 'memory',

  async load() {
    try {
      const db = await idb();
      const bytes = await tx(db, 'readonly', (s) => s.get(KEY));
      db.close();
      this.mode = 'indexeddb';
      return bytes instanceof Uint8Array ? bytes : bytes ? new Uint8Array(bytes) : null;
    } catch {
      try {
        const s = localStorage.getItem(LS_KEY);
        this.mode = 'localstorage';
        return s ? fromBase64(s) : null;
      } catch {
        this.mode = 'memory';
        return null;
      }
    }
  },

  async save(bytes) {
    try {
      const db = await idb();
      await tx(db, 'readwrite', (s) => s.put(bytes, KEY));
      db.close();
      this.mode = 'indexeddb';
      return true;
    } catch {
      try {
        localStorage.setItem(LS_KEY, toBase64(bytes));
        this.mode = 'localstorage';
        return true;
      } catch {
        this.mode = 'memory';
        return false;
      }
    }
  },

  async clear() {
    try {
      const db = await idb();
      await tx(db, 'readwrite', (s) => s.delete(KEY));
      db.close();
    } catch { /* ignore */ }
    try { localStorage.removeItem(LS_KEY); } catch { /* ignore */ }
  },
};
