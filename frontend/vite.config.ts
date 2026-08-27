import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// Same shape as the Lab 35 crm-ui config. The dev proxy is the one extra:
// it forwards /api -> Spring Boot on :8080 so the browser avoids CORS while
// the backend CORS config is still being set up. Leave VITE_API_BASE_URL empty
// to use it; set it to hit a backend directly (as the lab does).
//
// VITE_PROXY_TARGET repoints the proxy without editing this file — set it in a
// gitignored .env.local to demo against the course cluster while the browser
// keeps talking to localhost, which is what keeps CORS out of the picture.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': env.VITE_PROXY_TARGET || 'http://localhost:8080',
    },
  },
  test: {
    // Browser journeys are owned by Playwright under e2e/. Keep Vitest focused
    // on the component and API unit tests that live beside the source files.
    include: ['src/**/*.test.{ts,tsx}'],
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.ts',
  },
  }
})
