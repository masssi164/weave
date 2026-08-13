import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ command }) => ({
  // Keep the host Vite development URL unchanged while producing the exact
  // Server-owned production subtree.
  base: command === 'build' ? '/admin-console/' : '/',
  plugins: [react()],
  server: {
    port: 5173,
  },
}));
