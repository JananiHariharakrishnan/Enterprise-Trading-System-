import { describe, it, expect, vi } from 'vitest';

// Mock external decorator packages before importing the controller
vi.mock('@nestjs/throttler', () => ({ Throttle: () => () => {} }));
vi.mock('@nestjs/swagger', () => ({ ApiBearerAuth: () => () => {}, ApiTags: () => () => {} }));

import { AuthController } from '../src/app/auth/auth.controller.js';
import { AuthService } from '../src/app/auth/auth.service.js';

vi.mock('../src/app/auth/auth.service.js');

describe('AuthController (skeleton)', () => {
  const svc = new AuthService() as any;
  svc.register = vi.fn().mockResolvedValue({});
  svc.login = vi.fn().mockResolvedValue({});
  svc.refresh = vi.fn().mockResolvedValue({});
  svc.me = vi.fn().mockResolvedValue({});
  svc.updateProfile = vi.fn().mockResolvedValue({});

  const ctl = new AuthController(svc as any);

  it('register forwards to service', async () => {
    await ctl.register({ username: 'u', password: 'p', accountId: 1 } as any);
    expect(svc.register).toHaveBeenCalled();
  });

  it('login forwards to service', async () => {
    await ctl.login({ username: 'u', password: 'p' } as any);
    expect(svc.login).toHaveBeenCalled();
  });

  it('refresh forwards to service', async () => {
    await ctl.refresh({ refreshToken: 'r' } as any);
    expect(svc.refresh).toHaveBeenCalled();
  });

  it('me forwards to service', async () => {
    await ctl.me({ sub: 'u1' } as any);
    expect(svc.me).toHaveBeenCalled();
  });

  it('updateProfile forwards to service', async () => {
    await ctl.updateProfile({ sub: 'u1' } as any, { firstName: 'A' } as any);
    expect(svc.updateProfile).toHaveBeenCalled();
  });
});
