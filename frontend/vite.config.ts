import { resolve } from 'node:path';
import { defineConfig } from 'vite';

/**
 * Multi-page build (specification § 9.1, ADR-003): one HTML entry per registration
 * variant, no client-side router.
 */
export default defineConfig({
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    rollupOptions: {
      input: {
        external: resolve(import.meta.dirname, 'index.html'),
        student: resolve(import.meta.dirname, 'studentska-prijava/index.html'),
      },
    },
  },
  server: {
    port: 5173,
    proxy: {
      // In development the frontend and the backend are separate origins; proxying
      // /api keeps the browser on one origin, as it is behind nginx in production.
      '/api': { target: 'http://localhost:3000', changeOrigin: false },
    },
  },
});
