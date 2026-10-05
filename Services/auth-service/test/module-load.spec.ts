import { describe, it, expect } from 'vitest';

// A very small smoke test to ensure modules load and increase coverage for the module file
describe('module load', () => {
  it('auth module loads without throwing', async () => {
    const mod = await import('../src/app/auth/auth.module.js');
    expect(mod.AuthModule).toBeTruthy();
  });
});
