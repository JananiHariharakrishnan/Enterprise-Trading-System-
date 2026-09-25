import { describe, it, expect, vi, beforeEach } from 'vitest';
import * as repo from '../src/app/auth/repository/auth.repository.js';
import { db } from '../src/app/common/database.js';

vi.mock('../src/app/common/database.js');
vi.mock('argon2');
vi.mock('bcrypt');

describe('auth.repository', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('accountExists true/false', async () => {
    (db.query as any).mockResolvedValueOnce({ rows: [{ '?column?': 1 }], rowCount: 1 });
    expect(await repo.accountExists(1)).toBe(true);

    (db.query as any).mockResolvedValueOnce({ rows: [], rowCount: 0 });
    expect(await repo.accountExists(2)).toBe(false);
  });

  it('create and find user', async () => {
    (db.query as any).mockResolvedValueOnce({ rows: [{ id: 'u1', username: 'bob', account_id: 1, roles: ['CUSTOMER'], created_on: new Date().toISOString() }], rowCount: 1 });
    const user = await repo.createUser('bob', 1);
    expect(user.username).toBe('bob');

    (db.query as any).mockResolvedValueOnce({ rows: [{ id: 'u1', username: 'bob', account_id: 1, roles: ['CUSTOMER'], created_on: new Date().toISOString() }], rowCount: 1 });
    const found = await repo.findUserByUsername('bob');
    expect(found?.username).toBe('bob');
  });

  it('createCredential and findCredentialByUsername', async () => {
    const fakeHash = '$argon$hash';
    const argon2 = await import('argon2');
    (argon2.hash as any).mockResolvedValue(fakeHash);

    (db.query as any).mockResolvedValueOnce({ rows: [{ id: 'c1', account_id: 1, username: 'bob', password_hash: fakeHash, params: {}, created_at: new Date().toISOString(), updated_at: new Date().toISOString() }], rowCount: 1 });
    const cred = await repo.createCredential(1, 'bob', 'secret');
    expect(cred.username).toBe('bob');

    (db.query as any).mockResolvedValueOnce({ rows: [{ id: 'c1', account_id: 1, username: 'bob', password_hash: fakeHash, params: {}, created_at: new Date().toISOString(), updated_at: new Date().toISOString() }], rowCount: 1 });
    const fetched = await repo.findCredentialByUsername('bob');
    expect(fetched?.passwordHash).toBe(fakeHash);
  });

  it('verifyPassword timing and failure', async () => {
    const argon2 = await import('argon2');
    (argon2.verify as any).mockResolvedValue(true);

    const stored = { id: 'c1', accountId: 1, username: 'bob', passwordHash: '$argon$hash', params: {}, createdAt: new Date().toISOString() } as any;
    expect(await repo.verifyPassword(stored, 'secret')).toBe(true);

    (argon2.verify as any).mockRejectedValue(new Error('boom'));
    const warnSpy = vi.spyOn((await import('../src/app/common/logger.js')).logger, 'warn');
    expect(await repo.verifyPassword(stored, 'secret')).toBe(false);
    expect(warnSpy).toHaveBeenCalled();

    expect(await repo.verifyPassword(null, 'anything')).toBe(false);
  });

  it('refresh token save and consume paths', async () => {
    const tokens = await import('../src/app/auth/tokens/auth.tokens.js');
    const bcrypt = await import('bcrypt');
    // ensure parseRefreshToken is a mock
    if (!(tokens.parseRefreshToken as any).mock) {
      vi.spyOn(tokens, 'parseRefreshToken').mockImplementation(() => ({ tokenId: 't1', secret: 's1' }) as any);
    } else {
      (tokens.parseRefreshToken as any).mockReturnValue({ tokenId: 't1', secret: 's1' });
    }
    (bcrypt.hash as any).mockResolvedValue('$2b$hash');

    (db.query as any).mockResolvedValueOnce({ rows: [{ id: 't1', session_id: 't1', user_id: 'u1', token_hash: '$2b$hash', algorithm: 'bcrypt', expires_at: new Date(Date.now()+1000*60).toISOString(), consumed_at: null, revoked_at: null, replaced_by: null, created_at: new Date().toISOString(), updated_at: new Date().toISOString() }], rowCount: 1 });
    const saved = await repo.saveRefreshToken('r1', 'u1');
    expect(saved.userId).toBe('u1');

    // getRefreshTokenById -> not found
    (tokens.parseRefreshToken as any).mockReturnValue({ tokenId: 'missing', secret: 's2' });
    (db.query as any).mockResolvedValueOnce({ rows: [], rowCount: 0 });
    expect(await repo.consumeRefreshToken('missing')).toBeNull();
  });

  it('updateUserProfile throws when user missing and handles no-op update', async () => {
    // missing user
    (db.query as any).mockResolvedValueOnce({ rows: [], rowCount: 0 });
    await expect(repo.updateUserProfile('nope', {} as any)).rejects.toThrow('User not found');

    // existing user, no updates -> returns user
    const existing = { id: 'u2', username: 'alice', account_id: 2, roles: ['CUSTOMER'], created_on: new Date().toISOString() };
    (db.query as any).mockResolvedValueOnce({ rows: [existing], rowCount: 1 });
    const result = await repo.updateUserProfile('u2', {});
    expect(result.username).toBe('alice');

    // update with fields but update returns 0 rows -> returns original user
    (db.query as any).mockResolvedValueOnce({ rows: [existing], rowCount: 1 });
    // next call for update returns 0
    (db.query as any).mockResolvedValueOnce({ rows: [], rowCount: 0 });
    const res2 = await repo.updateUserProfile('u2', { firstName: 'A' });
    expect(res2.username).toBe('alice');

    // update returns updated row
    const updatedRow = { ...existing, first_name: 'A' };
    (db.query as any).mockResolvedValueOnce({ rows: [existing], rowCount: 1 });
    (db.query as any).mockResolvedValueOnce({ rows: [updatedRow], rowCount: 1 });
    const res3 = await repo.updateUserProfile('u2', { firstName: 'A' });
    expect(res3.firstName).toBe('A');
  });

  it('consumeRefreshToken various early exit branches', async () => {
    const tokens = await import('../src/app/auth/tokens/auth.tokens.js');
    // parsed null
    if ((tokens.parseRefreshToken as any).mock) {
      (tokens.parseRefreshToken as any).mockReturnValueOnce(null);
    } else {
      vi.spyOn(tokens, 'parseRefreshToken').mockImplementationOnce(() => null as any);
    }
    expect(await repo.consumeRefreshToken('bad')).toBeNull();

    // parsed ok but stored not found
    if ((tokens.parseRefreshToken as any).mock) {
      (tokens.parseRefreshToken as any).mockReturnValueOnce({ tokenId: 't2', secret: 's' });
    } else {
      vi.spyOn(tokens, 'parseRefreshToken').mockImplementationOnce(() => ({ tokenId: 't2', secret: 's' }) as any);
    }
    (db.query as any).mockResolvedValueOnce({ rows: [], rowCount: 0 });
    expect(await repo.consumeRefreshToken('t2')).toBeNull();

    // stored expired
    if ((tokens.parseRefreshToken as any).mock) {
      (tokens.parseRefreshToken as any).mockReturnValueOnce({ tokenId: 't3', secret: 's' });
    } else {
      vi.spyOn(tokens, 'parseRefreshToken').mockImplementationOnce(() => ({ tokenId: 't3', secret: 's' }) as any);
    }
    const expired = { id: 't3', session_id: 's', user_id: 'u', token_hash: 'h', algorithm: 'bcrypt', expires_at: new Date(Date.now()-10000).toISOString(), consumed_at: null, revoked_at: null, replaced_by: null, created_at: new Date().toISOString(), updated_at: new Date().toISOString() };
    (db.query as any).mockResolvedValueOnce({ rows: [expired], rowCount: 1 });
    expect(await repo.consumeRefreshToken('t3')).toBeNull();

    // algorithm mismatch -> warn and null
    if ((tokens.parseRefreshToken as any).mock) {
      (tokens.parseRefreshToken as any).mockReturnValueOnce({ tokenId: 't4', secret: 's' });
    } else {
      vi.spyOn(tokens, 'parseRefreshToken').mockImplementationOnce(() => ({ tokenId: 't4', secret: 's' }) as any);
    }
    const stored = { id: 't4', session_id: 's', user_id: 'u', token_hash: 'h', algorithm: 'sha256', expires_at: new Date(Date.now()+10000).toISOString(), consumed_at: null, revoked_at: null, replaced_by: null, created_at: new Date().toISOString(), updated_at: new Date().toISOString() };
    (db.query as any).mockResolvedValueOnce({ rows: [stored], rowCount: 1 });
    const warnSpy = vi.spyOn((await import('../src/app/common/logger.js')).logger, 'warn');
    expect(await repo.consumeRefreshToken('t4')).toBeNull();
    expect(warnSpy).toHaveBeenCalled();
  });
});
