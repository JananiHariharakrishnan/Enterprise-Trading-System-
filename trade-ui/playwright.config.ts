import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  timeout: 45_000,
  fullyParallel: false,
  retries: 0,
  use: {
    baseURL: 'http://localhost:4200',
    trace: 'on-first-retry'
  },
  webServer: [
    {
      command: 'npm run start -- --host 127.0.0.1 --port 4200',
      url: 'http://127.0.0.1:4200/login',
      cwd: '.',
      reuseExistingServer: true,
      timeout: 120_000
    },
    {
      command: 'npm run start:dev',
      url: 'http://127.0.0.1:3000/docs',
      cwd: '../services/auth-service',
      reuseExistingServer: true,
      timeout: 120_000
    }
  ]
});
