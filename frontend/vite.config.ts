import react from '@vitejs/plugin-react';
import { loadEnv } from 'vite';
import { defineConfig } from 'vitest/config';

export default defineConfig(({ mode }) => {
  const { BACKEND_URL = 'http://127.0.0.1:8080' } = loadEnv(mode, process.cwd(), '');
  const proxy = { '/api': BACKEND_URL };

  return {
    plugins: [react()],
    server: {
      port: 5173,
      strictPort: true,
      proxy,
    },
    preview: {
      proxy,
    },
    test: {
      environment: 'jsdom',
    },
  };
});
