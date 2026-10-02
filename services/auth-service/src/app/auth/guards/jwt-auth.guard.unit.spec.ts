import { describe, it, expect } from 'vitest';
import { JwtAuthGuard } from './jwt-auth.guard.js';

describe('JwtAuthGuard wiring', () => {
  it('can be instantiated and exposes canActivate', () => {
    const g = new JwtAuthGuard();
    expect(typeof g.canActivate).toBe('function');
  });
});
