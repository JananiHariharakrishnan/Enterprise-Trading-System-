import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ConflictException, UnauthorizedException } from '@nestjs/common';
import { AuthService } from '../src/app/auth/auth.service.js';

vi.mock('../src/app/auth/repository/auth.repository.js');
vi.mock('../src/app/auth/tokens/auth.tokens.js');

const repo = await import('../src/app/auth/repository/auth.repository.js');
const tokens = await import('../src/app/auth/tokens/auth.tokens.js');

describe('AuthService', () => {
  let svc: AuthService;

  beforeEach(() => {
    vi.resetAllMocks();
    svc = new AuthService();
  });

  it('register throws Conflict when username exists', async () => {
    (repo.findUserByUsername as any).mockResolvedValue({ id: 'u1' });
    await expect(svc.register({ username: 'u', password: 'p' })).rejects.toBeInstanceOf(ConflictException);
  });

  it('register throws Conflict when credential username exists', async () => {
    (repo.findUserByUsername as any).mockResolvedValue(null);
    (repo.findCredentialByUsername as any).mockResolvedValue({ id: 'c1' });
    await expect(svc.register({ username: 'u', password: 'p' })).rejects.toBeInstanceOf(ConflictException);
  });

  it('register throws Conflict when no account is available', async () => {
    (repo.findUserByUsername as any).mockResolvedValue(null);
    (repo.findCredentialByUsername as any).mockResolvedValue(null);
    (repo.deleteOrphanCredentials as any).mockResolvedValue(0);
    (repo.findAvailableAccountId as any).mockResolvedValue(null);
    await expect(svc.register({ username: 'u', password: 'p' })).rejects.toBeInstanceOf(ConflictException);
  });

  it('register success path', async () => {
    (repo.findUserByUsername as any).mockResolvedValue(null);
    (repo.findCredentialByUsername as any).mockResolvedValue(null);
    (repo.deleteOrphanCredentials as any).mockResolvedValue(0);
    (repo.findAvailableAccountId as any).mockResolvedValue(1);
    (repo.createCredential as any).mockResolvedValue({});
    (repo.createUser as any).mockResolvedValue({ id: 'u1', username: 'u', accountId: 1, roles: ['CUSTOMER'], createdOn: new Date().toISOString() });

    const out = await svc.register({ username: 'u', password: 'p' });
    expect(out.username).toBe('u');
    expect(out.id).toBe('u1');
  });

  it('register retries when account allocation races on unique constraint', async () => {
    (repo.findUserByUsername as any).mockResolvedValue(null);
    (repo.findCredentialByUsername as any).mockResolvedValue(null);
    (repo.deleteOrphanCredentials as any).mockResolvedValue(0);
    (repo.findAvailableAccountId as any).mockResolvedValueOnce(1).mockResolvedValueOnce(2);
    (repo.createCredential as any).mockResolvedValue({});
    (repo.createUser as any)
      .mockResolvedValueOnce(null)
      .mockResolvedValueOnce({ id: 'u2', username: 'u2', accountId: 2, roles: ['CUSTOMER'], createdOn: new Date().toISOString() });
    (repo.deleteCredentialByUsername as any).mockResolvedValue(undefined);

    const out = await svc.register({ username: 'u2', password: 'p' });
    expect(out.accountId).toBe(2);
    expect(repo.findAvailableAccountId).toHaveBeenCalledTimes(2);
    expect(repo.createCredential).toHaveBeenCalledTimes(2);
    expect(repo.deleteCredentialByUsername).toHaveBeenCalledWith('u2');
  });

  it('login throws Unauthorized on bad password', async () => {
    (repo.findCredentialByUsername as any).mockResolvedValue({});
    (repo.verifyPassword as any).mockResolvedValue(false);
    await expect(svc.login({ username: 'u', password: 'bad' })).rejects.toBeInstanceOf(UnauthorizedException);
  });

  it('login success path', async () => {
    (repo.findCredentialByUsername as any).mockResolvedValue({});
    (repo.verifyPassword as any).mockResolvedValue(true);
    (repo.findUserByUsername as any).mockResolvedValue({ id: 'u1', username: 'u' });
    (tokens.signAccessToken as any).mockReturnValue('at');
    (tokens.makeRefreshToken as any).mockReturnValue('rt');
    (repo.saveRefreshToken as any).mockResolvedValue({});

    const out = await svc.login({ username: 'u', password: 'p' });
    expect(out.accessToken).toBe('at');
    expect(out.refreshToken).toBe('rt');
  });

  it('refresh throws Unauthorized when consume fails', async () => {
    (repo.consumeRefreshToken as any).mockResolvedValue(null);
    await expect(svc.refresh({ refreshToken: 'x' })).rejects.toBeInstanceOf(UnauthorizedException);
  });

  it('refresh success path', async () => {
    (repo.consumeRefreshToken as any).mockResolvedValue({ id: 't1', userId: 'u1', sessionId: 's1' });
    (repo.findUserById as any).mockResolvedValue({ id: 'u1', username: 'u' });
    (tokens.signAccessToken as any).mockReturnValue('at');
    (tokens.makeRefreshToken as any).mockReturnValue('rt');
    (repo.saveRefreshToken as any).mockResolvedValue({});

    const out = await svc.refresh({ refreshToken: 'r' });
    expect(out.accessToken).toBe('at');
  });

  it('me throws Unauthorized when user missing', async () => {
    (repo.findUserById as any).mockResolvedValue(null);
    await expect(svc.me({ sub: 'no' } as any)).rejects.toBeInstanceOf(UnauthorizedException);
  });

  it('me success returns user view', async () => {
    (repo.findUserById as any).mockResolvedValue({ id: 'u1', username: 'u', accountId: 1, firstName: 'A', lastName: 'B', email: 'e', phoneNo: 'p', roles: ['CUSTOMER'], createdOn: new Date().toISOString() });
    const out = await svc.me({ sub: 'u1' } as any);
    expect(out.username).toBe('u');
  });

  it('updateProfile throws when current missing', async () => {
    (repo.findUserById as any).mockResolvedValue(null);
    await expect(svc.updateProfile('u', { firstName: 'A' } as any)).rejects.toBeInstanceOf(UnauthorizedException);
  });

  it('updateProfile success', async () => {
    (repo.findUserById as any).mockResolvedValue({ id: 'u1' });
    (repo.updateUserProfile as any).mockResolvedValue({ id: 'u1', username: 'u', accountId: 1, firstName: 'A', lastName: 'B', email: 'e', phoneNo: 'p', roles: ['CUSTOMER'], createdOn: new Date().toISOString() });
    const out = await svc.updateProfile('u1', { firstName: 'A' } as any);
    expect(out.firstName).toBe('A');
  });
});
