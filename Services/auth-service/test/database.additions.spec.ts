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

  it('deletes credentials that are not linked to a user', async () => {
    const db = createInMemoryDb();
    await db.query(
      'INSERT INTO users (username, account_id, roles, created_on) VALUES ($1,$2,$3,NOW())',
      ['kept', 1, ['CUSTOMER']],
    );
    await db.query(
      'INSERT INTO credentials (account_id, username, password_hash, params, created_at, updated_at) VALUES ($1,$2,$3,$4,NOW(),NOW())',
      [1, 'linked', 'h', JSON.stringify({})],
    );
    await db.query(
      'INSERT INTO credentials (account_id, username, password_hash, params, created_at, updated_at) VALUES ($1,$2,$3,$4,NOW(),NOW())',
      [2, 'orphan', 'h', JSON.stringify({})],
    );

    const deleted = await db.query(`
      DELETE FROM credentials c
      WHERE NOT EXISTS (
        SELECT 1
        FROM users u
        WHERE u.account_id = c.account_id
      )
    `);
    expect(deleted.rowCount).toBe(1);

    const remaining = await db.query('SELECT * FROM credentials WHERE username = $1 LIMIT 1', ['linked']);
    expect(remaining.rowCount).toBe(1);
  });

  it('returns no row when updating a missing user and ignores unknown sql', async () => {
    const db = createInMemoryDb();
    const inserted = await db.query(
      'INSERT INTO users (username, account_id, roles, created_on) VALUES ($1,$2,$3,NOW())',
      ['ada', 1, ['CUSTOMER']],
    );
    const updated = await db.query('UPDATE users SET first_name = $2 WHERE id = $1', [inserted.rows[0].id, 'Ada']);
    expect(updated.rowCount).toBe(1);

    const missing = await db.query('UPDATE users SET first_name = $2 WHERE id = $1', ['missing', 'Ada']);
    expect(missing.rowCount).toBe(0);

    const unknown = await db.query('SELECT now()');
    expect(unknown).toEqual({ rows: [], rowCount: 0 });
  });

  it('leaves refresh-token updates unchanged when the row is missing', async () => {
    const db = createInMemoryDb();
    const consumed = await db.query('UPDATE refresh_tokens SET consumed_at = NOW(), updated_at = NOW() WHERE id = $1', ['missing']);
    expect(consumed.rowCount).toBe(0);
    const replaced = await db.query('UPDATE refresh_tokens SET replaced_by = $1, updated_at = NOW() WHERE id = $2', ['next', 'missing']);
    expect(replaced.rowCount).toBe(0);
  });
});
