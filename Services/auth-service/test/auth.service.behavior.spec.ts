import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ConflictException, UnauthorizedException } from '@nestjs/common';

vi.mock('../src/app/auth/repository/auth.repository.js');
vi.mock('../src/app/auth/tokens/auth.tokens.js');

const repo = await import('../src/app/auth/repository/auth.repository.js');
const tokens = await import('../src/app/auth/tokens/auth.tokens.js');
const { AuthService } = await import('../src/app/auth/auth.service.js');

describe('AuthService additional behavior', () => {
  let svc: any;

  beforeEach(() => {
    vi.resetAllMocks();
    svc = new AuthService();
  });

  it('register logs when orphan credentials removed and proceeds', async () => {
    (repo.findUserByUsername as any).mockResolvedValue(null);
    (repo.findCredentialByUsername as any).mockResolvedValue(null);
    (repo.deleteOrphanCredentials as any).mockResolvedValue(2);
    (repo.findAvailableAccountId as any).mockResolvedValue(1);
    (repo.createCredential as any).mockResolvedValue({});
    (repo.createUser as any).mockResolvedValue({ id: 'u1', username: 'u', accountId: 1, roles: ['CUSTOMER'], createdOn: new Date().toISOString() });

    const out = await svc.register({ username: 'u', password: 'p' });
    expect(out.username).toBe('u');
  });

  it('register rethrows non-allocation error from createCredential', async () => {
    (repo.findUserByUsername as any).mockResolvedValue(null);
    (repo.findCredentialByUsername as any).mockResolvedValue(null);
    (repo.deleteOrphanCredentials as any).mockResolvedValue(0);
    (repo.findAvailableAccountId as any).mockResolvedValue(1);
    (repo.createCredential as any).mockRejectedValue(new Error('db-down'));

    await expect(svc.register({ username: 'u', password: 'p' })).rejects.toThrow('db-down');
  });

  it('register retries when createCredential conflicts then succeeds', async () => {
    (repo.findUserByUsername as any).mockResolvedValue(null);
    (repo.findCredentialByUsername as any).mockResolvedValue(null);
    (repo.deleteOrphanCredentials as any).mockResolvedValue(0);
    (repo.findAvailableAccountId as any).mockResolvedValueOnce(1).mockResolvedValueOnce(2);

    const conflictErr: any = new Error('conflict');
    conflictErr.code = '23505';
    conflictErr.constraint = 'uq_credentials_account_id';

    (repo.createCredential as any).mockRejectedValueOnce(conflictErr).mockResolvedValueOnce({});
    (repo.createUser as any).mockResolvedValue({ id: 'uX', username: 'uX', accountId: 2, roles: ['CUSTOMER'], createdOn: new Date().toISOString() });

    const out = await svc.register({ username: 'uX', password: 'p' });
    expect(out.accountId).toBe(2);
    expect((repo.createCredential as any)).toHaveBeenCalledTimes(2);
  });

  it('register rethrows non-allocation error from createUser', async () => {
    (repo.findUserByUsername as any).mockResolvedValue(null);
    (repo.findCredentialByUsername as any).mockResolvedValue(null);
    (repo.deleteOrphanCredentials as any).mockResolvedValue(0);
    (repo.findAvailableAccountId as any).mockResolvedValue(1);
    (repo.createCredential as any).mockResolvedValue({});
    (repo.createUser as any).mockRejectedValue(new Error('boom'));

    await expect(svc.register({ username: 'u', password: 'p' })).rejects.toThrow('boom');
  });

  it('login throws when credential present but user missing', async () => {
    (repo.findCredentialByUsername as any).mockResolvedValue({});
    (repo.verifyPassword as any).mockResolvedValue(true);
    (repo.findUserByUsername as any).mockResolvedValue(null);

    const svc2 = new AuthService();
    await expect(svc2.login({ username: 'u', password: 'p' })).rejects.toBeInstanceOf(UnauthorizedException);
  });
});
