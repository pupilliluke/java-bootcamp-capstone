import { defineConfig, devices } from '@playwright/test'

const backendCommand =
  process.platform === 'win32'
    ? '..\\backend\\mvnw.cmd -f ..\\backend\\pom.xml spring-boot:run'
    : '../backend/mvnw -f ../backend/pom.xml spring-boot:run'

export default defineConfig({
  testDir: './e2e',
  outputDir: './test-results',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: [
    ['list'],
    ['html', { open: 'never', outputFolder: 'playwright-report' }],
  ],
  expect: {
    timeout: 10_000,
  },
  use: {
    baseURL: 'http://127.0.0.1:5173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: [
    {
      command: backendCommand,
      url: 'http://127.0.0.1:8080/actuator/health/readiness',
      reuseExistingServer: !process.env.CI,
      timeout: 180_000,
      stdout: 'pipe',
      stderr: 'pipe',
    },
    {
      command: 'npm run dev -- --host 127.0.0.1',
      url: 'http://127.0.0.1:5173',
      reuseExistingServer: !process.env.CI,
      timeout: 60_000,
      stdout: 'pipe',
      stderr: 'pipe',
      // Keep Google Sign-In out of the e2e build: the GSI script and its network
      // calls add nothing to the journey under test and only introduce flake.
      // LoginPage reads this via import.meta.env.VITE_ENABLE_GSI.
      env: { ...process.env, VITE_ENABLE_GSI: 'false' },
    },
  ],
})
