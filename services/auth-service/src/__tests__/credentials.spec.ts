import { createCredential, findCredentialByUsername, verifyPassword } from '../lib/credentialsStore';
import argon2 from 'argon2';

describe('credentials store', () => {
  const username = `testuser_${Date.now()}`;
  const accountId = 1;
  const password = 'correct-horse-battery-staple';

  test('correct password verifies', async () => {
    const created = await createCredential(accountId, username, password);
    const stored = await findCredentialByUsername(username);
    expect(stored).not.toBeNull();
    const ok = await verifyPassword(stored, password);
    expect(ok).toBe(true);
  });

  test('incorrect password fails verification', async () => {
    const stored = await findCredentialByUsername(username);
    const ok = await verifyPassword(stored, 'wrong-password');
    expect(ok).toBe(false);
  });

  test('no general-purpose digest used', async () => {
    const stored = await findCredentialByUsername(username);
    expect(stored).not.toBeNull();
    expect(stored!.algorithm).toBe('argon2id');
    // static assert by checking stored hash starts with $argon2id$
    expect(stored!.password_hash.startsWith('$argon2id$')).toBe(true);
  });
});