import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    maxWorkers: 1,
    pool: 'threads',
    fileParallelism: false,
    isolate: false,
    setupFiles: './src/test/setup.ts',
    css: true,
  },
});
