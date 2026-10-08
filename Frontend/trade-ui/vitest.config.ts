import { defineConfig } from 'vitest/config';
import angular from '@analogjs/vite-plugin-angular';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

export default defineConfig({
  plugins: [
    angular(),
  ],

  test: {
    globals: true,
    environment: 'jsdom',

    setupFiles: [
      resolve(__dirname, 'src/test-setup.ts'),
    ],

    include: [
      'src/**/*.spec.ts',
    ],

    exclude: [
      '**/node_modules/**',
      '**/dist/**',
      '**/e2e/**',
    ],

    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov'],
      exclude: [
        '**/node_modules/**',
        '**/dist/**',
        '**/e2e/**',
        '**/*.spec.ts',
        '**/generated/**',
      ],
    },
  },
});