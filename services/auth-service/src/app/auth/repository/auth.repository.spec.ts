import { beforeEach, describe, expect, it, vi } from 'vitest';

const USERNAME = `testuser_${Date.now()}`;
const PASSWORD = 'correct-horse-battery-staple';

describe('credential repository', () => {
  beforeEach(() => {
    vi.resetModules();
  });

  it('correct password verifies', async () => {
    const repo = await import('./auth.repository.js');
    await repo.createCredential(1, USERNAME, PASSWORD);
    const stored = await repo.findCredentialByUsername(USERNAME);

    expect(stored).not.toBeNull();
    await expect(repo.verifyPassword(stored, PASSWORD)).resolves.toBe(true);
  });

  it('incorrect password fails verification', async () => {
    const repo = await import('./auth.repository.js');
    await repo.createCredential(1, `${USERNAME}_wrong`, PASSWORD);
    const stored = await repo.findCredentialByUsername(`${USERNAME}_wrong`);

    await expect(repo.verifyPassword(stored, 'wrong-password')).resolves.toBe(false);
  });

  it('a general-purpose digest such as MD5 or SHA is not used', async () => {
    const repo = await import('./auth.repository.js');
    await repo.createCredential(1, `${USERNAME}_hash`, PASSWORD);
    const stored = await repo.findCredentialByUsername(`${USERNAME}_hash`);

    expect(stored?.passwordHash.startsWith('$argon2id$')).toBe(true);
  });

  it('stores only a hash of the refresh token secret', async () => {
    const repo = await import('./auth.repository.js');
    const { makeRefreshToken, parseRefreshToken } = await import('../tokens/auth.tokens.js');
    const refreshToken = makeRefreshToken();
    const parsed = parseRefreshToken(refreshToken);

    expect(parsed).not.toBeNull();

    const stored = await repo.saveRefreshToken(refreshToken, '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f');

    expect(stored.algorithm).toBe('bcrypt');
    expect(stored.tokenHash).not.toBe(refreshToken);
    expect(stored.tokenHash).not.toBe(parsed!.secret);
    expect(stored.consumedAt).toBeNull();
    expect(stored.revokedAt).toBeNull();
  });

  it('revokes the whole refresh-token session when an exchanged token is replayed', async () => {
    const repo = await import('./auth.repository.js');
    const { makeRefreshToken } = await import('../tokens/auth.tokens.js');
    const userId = '123e4567-e89b-12d3-a456-426614174000';

    const originalToken = makeRefreshToken();
    const originalRecord = await repo.saveRefreshToken(originalToken, userId);

    const consumedOriginal = await repo.consumeRefreshToken(originalToken);
    expect(consumedOriginal?.id).toBe(originalRecord.id);

    const rotatedToken = makeRefreshToken();
    await repo.saveRefreshToken(rotatedToken, userId, originalRecord.sessionId, originalRecord.id);

    const replayedOriginal = await repo.consumeRefreshToken(originalToken);
    expect(replayedOriginal).toBeNull();

    const afterReplay = await repo.consumeRefreshToken(rotatedToken);
    expect(afterReplay).toBeNull();
  });
});

