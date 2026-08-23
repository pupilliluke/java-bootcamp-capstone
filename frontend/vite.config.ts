import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Same shape as the Lab 35 crm-ui config. The dev proxy is the one extra:
// it forwards /api -> Spring Boot on :8080 so the browser avoids CORS while
// the backend CORS config is still being set up. Leave VITE_API_BASE_URL empty
// to use it; set it to hit a backend directly (as the lab does).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.ts',
  },
})
