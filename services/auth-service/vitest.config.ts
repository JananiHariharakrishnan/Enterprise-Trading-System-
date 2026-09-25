import { defineConfig } from 'vitest/config';
import tsconfigPaths from 'vite-tsconfig-paths';

export default defineConfig({
  // Resolves the path aliases declared in tsconfig.json, including the ones
  // added by `nest g library`.
  plugins: [tsconfigPaths()],
  test: {
    globals: true,
    root: './',
    include: ['**/*.spec.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov', 'json'],
      all: true,
      include: ['src/**/*.ts'],
        exclude: [
          'test/**',
          '**/*.d.ts',
          'src/app.module.ts',
          'src/main.ts',
          'src/app/configure-app.ts',
          'src/app/auth/role.enum.ts',
          'src/app/auth/decorators/current-user.decorator.ts'
        ],
    },
  },
});
