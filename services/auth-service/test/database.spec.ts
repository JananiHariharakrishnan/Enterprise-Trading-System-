import { describe, it, expect } from 'vitest';
import { createInMemoryDb } from '../src/app/common/database.js';

describe('createInMemoryDb', () => {
  const db = createInMemoryDb();

  it('accounts lookup', async () => {
    const r = await db.query('SELECT 1 FROM accounts WHERE id = $1', [1]);
    expect(r.rowCount).toBe(1);
    const r2 = await db.query('SELECT 1 FROM accounts WHERE id = $1', [999]);
    expect(r2.rowCount).toBe(0);
  });

  it('users insert and find by username/id/account', async () => {
    const insertRes = await db.query(
      'INSERT INTO users (username, account_id, roles, created_on) VALUES ($1,$2,$3,NOW())',
      ['u1', 1, ['CUSTOMER']],
    );
    const inserted = insertRes.rows[0];
    const byName = await db.query('SELECT id, username FROM users WHERE username = $1 LIMIT 1', ['u1']);
    expect(byName.rowCount).toBe(1);
    const byId = await db.query('SELECT id, username FROM users WHERE id = $1 LIMIT 1', [inserted.id]);
    expect(byId.rowCount).toBe(1);
    const byAccount = await db.query('SELECT id, username FROM users WHERE account_id = $1 LIMIT 1', [1]);
    expect(byAccount.rowCount).toBe(1);
  });

  it('credentials insert and select', async () => {
    await db.query('INSERT INTO credentials (account_id, username, password_hash, params, created_at, updated_at) VALUES ($1,$2,$3,$4,NOW(),NOW())', [1, 'u1', 'h', JSON.stringify({})]);
    const res = await db.query('SELECT * FROM credentials WHERE username = $1 LIMIT 1', ['u1']);
    expect(res.rowCount).toBe(1);
  });

  it('finds next available account not linked in credentials', async () => {
    const isolatedDb = createInMemoryDb();

    const available = await isolatedDb.query(
      `
      SELECT a.id
      FROM accounts a
      WHERE NOT EXISTS (
        SELECT 1
        FROM users u
        WHERE u.account_id = a.id
      )
      ORDER BY random()
      LIMIT 1
      `,
      [],
    );

    expect(available.rowCount).toBe(1);
    expect(available.rows[0].id).toBe(1);

    await isolatedDb.query('INSERT INTO credentials (account_id, username, password_hash, params, created_at, updated_at) VALUES ($1,$2,$3,$4,NOW(),NOW())', [1, 'used-account', 'h', JSON.stringify({})]);

    const noneAvailable = await isolatedDb.query(
      `
      SELECT a.id
      FROM accounts a
      WHERE NOT EXISTS (
        SELECT 1
        FROM users u
        WHERE u.account_id = a.id
      )
      ORDER BY random()
      LIMIT 1
      `,
      [],
    );

    expect(noneAvailable.rowCount).toBe(0);
  });

  it('refresh tokens lifecycle', async () => {
    await db.query('INSERT INTO refresh_tokens (id, session_id, user_id, token_hash, algorithm, expires_at, consumed_at, revoked_at, replaced_by, created_at, updated_at) VALUES ($1,$2,$3,$4,$5,$6,NULL,NULL,NULL,NOW(),NOW())', ['t1', 's1', 'u1', 'h', 'bcrypt', new Date(Date.now() + 10000).toISOString()]);
    const get = await db.query('SELECT id, session_id FROM refresh_tokens WHERE id = $1 LIMIT 1', ['t1']);
    expect(get.rowCount).toBe(1);
    await db.query('UPDATE refresh_tokens SET consumed_at = NOW(), updated_at = NOW() WHERE id = $1', ['t1']);
    const upd = await db.query('SELECT id, session_id FROM refresh_tokens WHERE id = $1 LIMIT 1', ['t1']);
    expect(upd.rowCount).toBe(1);
    await db.query('UPDATE refresh_tokens SET revoked_at = NOW(), updated_at = NOW() WHERE session_id = $1 AND revoked_at IS NULL', ['s1']);
    const rev = await db.query('SELECT id, session_id FROM refresh_tokens WHERE id = $1 LIMIT 1', ['t1']);
    expect(rev.rowCount).toBe(1);
  });
});
