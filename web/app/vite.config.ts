import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vite'

// The dev server proxies the Memos API so the page and the server share an origin, as they
// do behind the container's nginx. Point MEMOS_URL at any Memos v0.30 server.
const memos = process.env.MEMOS_URL ?? 'http://localhost:5230'

export default defineConfig({
  esbuild: { jsx: 'automatic', jsxImportSource: 'preact' },
  // The Kotlin library is linked from its build folder, which has no node_modules of its
  // own; its time-zone packages are resolved from here instead.
  resolve: {
    dedupe: ['@js-joda/core', '@js-joda/timezone'],
    alias: { ws: fileURLToPath(new URL('./src/ws-stub.ts', import.meta.url)) },
  },
  build: {
    target: 'es2022',
    sourcemap: false,
    chunkSizeWarningLimit: 4000,
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: memos, changeOrigin: true },
      '/file': { target: memos, changeOrigin: true },
    },
  },
})
