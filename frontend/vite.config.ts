import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// Same shape as the Lab 35 crm-ui config. The dev proxy is the one extra: it
// forwards /api -> Spring Boot on :8080 so `npm run dev` stays same-origin --
// the browser talks only to localhost:5173 and Vite forwards server-side. That
// is not a stopgap for missing CORS config; same-origin is the deliberate
// stance everywhere. The deployed stack reaches it a different way, by serving
// the built UI and the API behind one ingress host (k8s/ingress.yaml), so there
// is no CorsConfigurationSource bean by design -- see docs/threat-model.md.
// Leave VITE_API_BASE_URL empty to use the proxy; setting it to a different
// origin makes requests cross-origin and would need that bean added first.
//
// VITE_PROXY_TARGET repoints the proxy without editing this file — set it in a
// gitignored .env.local to demo against the course cluster while the browser
// keeps talking to localhost, which is what keeps it same-origin.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': env.VITE_PROXY_TARGET || 'http://localhost:8080',
      // The Settings page's connection panel reads /actuator/health, which is
      // not under /api and so would otherwise be served by Vite as the SPA
      // index. vercel.json already rewrites this prefix for deployments; this
      // is the same rule for the dev server.
      '/actuator': env.VITE_PROXY_TARGET || 'http://localhost:8080',
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
