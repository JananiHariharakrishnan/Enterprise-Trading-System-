import { describe, it, expect } from 'vitest';
import { createInMemoryDb } from '../src/app/common/database.js';

describe('database skeleton tests', () => {
  it('createInMemoryDb basic branches', async () => {
    const db = createInMemoryDb() as any;
    // TODO: test accounts lookup, user insert, credential insert, refresh token insert and updates
    const r = await db.query('SELECT 1 FROM accounts WHERE id = $1 LIMIT 1', [1]);
    expect(r.rowCount).toBe(1);
  });

  it('refresh token update branches', async () => {
    const db = createInMemoryDb() as any;
    const ins = await db.query('INSERT INTO REFRESH_TOKENS (id, session_id, user_id, token_hash, algorithm, expires_at) VALUES (...)', ['t1','s','u','h','bcrypt', new Date().toISOString()]);
    expect(ins.rowCount).toBe(1);
  });
});
