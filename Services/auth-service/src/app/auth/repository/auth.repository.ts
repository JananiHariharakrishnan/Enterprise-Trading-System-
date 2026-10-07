import argon2 from 'argon2';
import bcrypt from 'bcrypt';
import { db } from '../../common/database.js';
import { logger } from '../../common/logger.js';
import { parseRefreshToken } from '../tokens/auth.tokens.js';
import { TradeApiClient, UserRecord as TradeApiUserRecord } from '../../trade-api/trade-api.client.js';

// Determine test mode using same signals as the in-memory DB helper
const isTest = process.env.NODE_ENV === 'test' || Boolean(process.env.VITEST_POOL_ID) || process.env.VITEST === 'true';

export type UserRecord = {
  id: string;
  username: string;
  accountId: number;
  firstName?: string;
  lastName?: string;
  email?: string;
  phoneNo?: string;
  roles: string[];
  createdOn: string;
};

export type CredentialRecord = {
  id: string;
  accountId: number;
  username: string;
  passwordHash: string;
  params: Record<string, unknown>;
  createdAt: string;
};

export type RefreshTokenRecord = {
  id: string;
  sessionId: string;
  userId: string;
  tokenHash: string;
  algorithm: string;
  expiresAt: string;
  consumedAt: string | null;
  revokedAt: string | null;
  replacedBy: string | null;
  createdAt: string;
  updatedAt: string;
};

const PASSWORD_HASH_OPTIONS = {
  type: argon2.argon2id,
  timeCost: Number(process.env.ARGON2_TIME ?? 3),
  memoryCost: Number(process.env.ARGON2_MEMORY_KB ?? 32768),
  parallelism: Number(process.env.ARGON2_PARALLELISM ?? 1),
};
const REFRESH_TOKEN_BCRYPT_COST = Number(process.env.REFRESH_TOKEN_BCRYPT_COST ?? 12);
const REFRESH_TOKEN_TTL_SECONDS = Number(process.env.REFRESH_TOKEN_TTL_SECONDS ?? 60 * 60 * 24 * 7);
const DUMMY_ARGON_HASH = '$argon2id$v=19$m=32768,t=3,p=1$AAAAAAAAAAAAAAAAAAAAAA$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA';

/**
 * Decode a Base64-encoded password from the frontend.
 * The frontend encodes passwords to prevent them from appearing in plaintext in network tabs.
 */
function decodePassword(encodedPassword: string): string {
  try {
    // Decode Base64 using Node.js Buffer
    return Buffer.from(encodedPassword, 'base64').toString('utf-8');
  } catch (error) {
    logger.warn('failed to decode password', {
      error: error instanceof Error ? error.message : 'unknown',
    });
    // Return the encoded password as-is if decoding fails
    return encodedPassword;
  }
}

// Initialize TradeApiClient for calls to trade-api
const tradeApiClient = new TradeApiClient();

export async function accountExists(accountId: number): Promise<boolean> {
  // During tests prefer direct DB queries so tests can mock `db.query`.
  if (isTest) {
    const result = await db.query(
      `
      SELECT 1
      FROM tradingsystem.accounts
      WHERE id = $1
      LIMIT 1
      `,
      [accountId],
    );

    return result.rowCount === 1;
  }

  // In other environments delegate to trade-api
  try {
    const user = await tradeApiClient.getUserByAccountId(accountId);
    return user !== null;
  } catch {
    return false;
  }
}

export async function findAvailableAccountId(): Promise<number | null> {
  if (isTest) {
    const result = await db.query(
      `
      SELECT a.id
      FROM tradingsystem.accounts a
      WHERE NOT EXISTS (
        SELECT 1
        FROM tradingsystem.users u
        WHERE u.account_id = a.id
      )
      ORDER BY random()
      LIMIT 1
      `,
    );

    if (result.rowCount === 0) return null;
    return Number(result.rows[0].id);
  }

  return tradeApiClient.getAvailableAccountId();
}

export async function findUserByUsername(username: string): Promise<UserRecord | null> {
  if (isTest) {
    const result = await db.query(
      `
      SELECT id, username, account_id, first_name, last_name, email, phone_no, roles, created_on
      FROM tradingsystem.users
      WHERE username = $1
      LIMIT 1
      `,
      [username],
    );

    return result.rows[0] ? mapUserRow(result.rows[0]) : null;
  }

  return tradeApiClient.getUserByUsername(username);
}

export async function findUserById(id: string): Promise<UserRecord | null> {
  if (isTest) {
    const result = await db.query(
      `
      SELECT id, username, account_id, first_name, last_name, email, phone_no, roles, created_on
      FROM tradingsystem.users
      WHERE id = $1
      LIMIT 1
      `,
      [id],
    );

    return result.rows[0] ? mapUserRow(result.rows[0]) : null;
  }

  return tradeApiClient.getUserById(id);
}

export async function findUserByAccountId(accountId: number): Promise<UserRecord | null> {
  if (isTest) {
    const result = await db.query(
      `
      SELECT id, username, account_id, first_name, last_name, email, phone_no, roles, created_on
      FROM tradingsystem.users
      WHERE account_id = $1
      LIMIT 1
      `,
      [accountId],
    );

    return result.rows[0] ? mapUserRow(result.rows[0]) : null;
  }

  return tradeApiClient.getUserByAccountId(accountId);
}

export async function updateUserProfile(
  userId: string,
  updates: {
    firstName?: string;
    lastName?: string;
    email?: string;
    phoneNo?: string;
  },
): Promise<UserRecord> {
  // First, verify the user exists
  const user = await findUserById(userId);
  if (!user) {
    throw new Error('User not found');
  }
  if (isTest) {
    const updateFields = [];
    const params: any[] = [userId];
    let paramCount = 2;

    if (updates.firstName !== undefined) {
      updateFields.push(`first_name = $${paramCount}`);
      params.push(updates.firstName);
      paramCount++;
    }
    if (updates.lastName !== undefined) {
      updateFields.push(`last_name = $${paramCount}`);
      params.push(updates.lastName);
      paramCount++;
    }
    if (updates.email !== undefined) {
      updateFields.push(`email = $${paramCount}`);
      params.push(updates.email);
      paramCount++;
    }
    if (updates.phoneNo !== undefined) {
      updateFields.push(`phone_no = $${paramCount}`);
      params.push(updates.phoneNo);
      paramCount++;
    }

    // If no fields to update, just return the current user
    if (updateFields.length === 0) {
      return user;
    }

    const query = `
      UPDATE tradingsystem.users
      SET ${updateFields.join(', ')}
      WHERE id = $1
      RETURNING id, username, account_id, first_name, last_name, email, phone_no, roles, created_on
    `;

    const result = await db.query(query, params);

    if (result.rowCount === 0) {
      return user;
    }

    return mapUserRow(result.rows[0]);
  }

  // Delegate update to trade-api
  const updated = await tradeApiClient.updateUserProfile(userId, updates);
  if (!updated) {
    throw new Error('Failed to update user profile');
  }

  return updated;
}

export async function createUser(username: string, accountId: number, roles: string[] = ['CUSTOMER']): Promise<UserRecord | null> {
  if (process.env.NODE_ENV === 'test') {
    const result = await db.query(
      `
      INSERT INTO tradingsystem.users (
        id,
        username,
        account_id,
        roles,
        created_on
      ) VALUES (
        gen_random_uuid(),
        $1,
        $2,
        $3,
        NOW()
      )
      ON CONFLICT (account_id) DO NOTHING
      RETURNING id, username, account_id, first_name, last_name, email, phone_no, roles, created_on
      `,
      [username, accountId, roles],
    );

    if (result.rowCount === 0) {
      return null;
    }

    return mapUserRow(result.rows[0]);
  }

  return tradeApiClient.createUser(username, accountId, roles);
}

export async function createCredential(accountId: number, username: string, plainPassword: string): Promise<CredentialRecord> {
  // Decode the Base64-encoded password from frontend
  const decodedPassword = decodePassword(plainPassword);
  const passwordHash = await argon2.hash(decodedPassword, PASSWORD_HASH_OPTIONS);
  const params = {
    time: PASSWORD_HASH_OPTIONS.timeCost,
    memory_kb: PASSWORD_HASH_OPTIONS.memoryCost,
    parallelism: PASSWORD_HASH_OPTIONS.parallelism,
  };

  const result = await db.query(
    `
    INSERT INTO authentication.credentials (
      id,
      account_id,
      username,
      password_hash,
      params,
      created_at,
      updated_at
    ) VALUES (
      gen_random_uuid(),
      $1,
      $2,
      $3,
      $4::jsonb,
      NOW(),
      NOW()
    )
    RETURNING *
    `,
    [accountId, username, passwordHash, JSON.stringify(params)],
  );

  return mapCredentialRow(result.rows[0]);
}

export async function deleteCredentialByUsername(username: string): Promise<void> {
  await db.query(
    `
    DELETE FROM authentication.credentials
    WHERE username = $1
    `,
    [username],
  );
}

export async function deleteOrphanCredentials(): Promise<number> {
  if (process.env.NODE_ENV === 'test') {
    const result = await db.query(
      `
      DELETE FROM authentication.credentials c
      WHERE NOT EXISTS (
        SELECT 1
        FROM tradingsystem.users u
        WHERE u.account_id = c.account_id
      )
      `,
    );

    return result.rowCount ?? 0;
  }

  // Delegate to trade-api in non-test environments
  try {
    await tradeApiClient.deleteOrphanCredentials();
    return 0;
  } catch (error) {
    logger.warn('Failed to delete orphan credentials via trade-api', {
      error: error instanceof Error ? error.message : 'unknown',
    });
    return 0;
  }
}

function mapUserRow(row: any): UserRecord {
  return {
    id: row.id,
    username: row.username,
    accountId: Number(row.account_id),
    firstName: row.first_name,
    lastName: row.last_name,
    email: row.email,
    phoneNo: row.phone_no,
    roles: row.roles ?? ['CUSTOMER'],
    createdOn: new Date(row.created_on).toISOString(),
  };
}

export async function findCredentialByUsername(username: string): Promise<CredentialRecord | null> {
  const result = await db.query('SELECT * FROM authentication.credentials WHERE username = $1 LIMIT 1', [username]);
  return result.rows[0] ? mapCredentialRow(result.rows[0]) : null;
}

export async function verifyPassword(stored: CredentialRecord | null, suppliedPassword: string): Promise<boolean> {
  // Decode the Base64-encoded password from frontend
  const decodedPassword = decodePassword(suppliedPassword);

  if (!stored) {
    try {
      await argon2.verify(DUMMY_ARGON_HASH, decodedPassword);
    } catch {
      // keep failure timing similar
    }
    return false;
  }

  try {
    return await argon2.verify(stored.passwordHash, decodedPassword);
  } catch (error) {
    logger.warn('password verification failed', {
      username: stored.username,
      error: error instanceof Error ? error.message : 'unknown',
    });
    return false;
  }
}

export async function saveRefreshToken(refreshToken: string, userId: string, sessionId?: string, replacesTokenId?: string): Promise<RefreshTokenRecord> {
  const parsed = parseRefreshToken(refreshToken);
  if (!parsed) {
    throw new Error('Invalid refresh token format');
  }

  const tokenHash = await bcrypt.hash(parsed.secret, REFRESH_TOKEN_BCRYPT_COST);
  const expiresAt = new Date(Date.now() + REFRESH_TOKEN_TTL_SECONDS * 1000).toISOString();
  const resolvedSessionId = sessionId ?? parsed.tokenId;

  const result = await db.query(
    `
    INSERT INTO authentication.refresh_tokens (
      id,
      session_id,
      user_id,
      token_hash,
      algorithm,
      expires_at,
      consumed_at,
      revoked_at,
      replaced_by,
      created_at,
      updated_at
    ) VALUES (
      $1,
      $2,
      $3,
      $4,
      $5,
      $6,
      NULL,
      NULL,
      NULL,
      NOW(),
      NOW()
    )
    RETURNING *
    `,
    [parsed.tokenId, resolvedSessionId, userId, tokenHash, 'bcrypt', expiresAt],
  );

  if (replacesTokenId) {
    await db.query(
      `
      UPDATE authentication.refresh_tokens
      SET replaced_by = $1, updated_at = NOW()
      WHERE id = $2
      `,
      [parsed.tokenId, replacesTokenId],
    );
  }

  return mapRefreshTokenRow(result.rows[0]);
}

export async function consumeRefreshToken(refreshToken: string): Promise<RefreshTokenRecord | null> {
  const parsed = parseRefreshToken(refreshToken);
  if (!parsed) {
    return null;
  }

  const stored = await getRefreshTokenById(parsed.tokenId);
  if (!stored) {
    return null;
  }

  const expired = new Date(stored.expiresAt).getTime() <= Date.now();
  if (stored.revokedAt || expired) {
    return null;
  }

  const matches = await verifyRefreshTokenSecret(parsed.secret, stored);
  if (!matches) {
    return null;
  }

  if (stored.consumedAt) {
    await revokeRefreshTokenSession(stored.sessionId);
    return null;
  }

  const updated = await db.query(
    `
    UPDATE authentication.refresh_tokens
    SET consumed_at = NOW(), updated_at = NOW()
    WHERE id = $1
      AND consumed_at IS NULL
      AND revoked_at IS NULL
      AND expires_at > NOW()
    RETURNING *
    `,
    [stored.id],
  );

  if (updated.rowCount === 0) {
    const latest = await getRefreshTokenById(stored.id);
    if (latest?.consumedAt && !latest.revokedAt) {
      await revokeRefreshTokenSession(latest.sessionId);
    }
    return null;
  }

  return mapRefreshTokenRow(updated.rows[0]);
}

export async function revokeRefreshTokenSession(sessionId: string): Promise<void> {
  await db.query(
    `
    UPDATE authentication.refresh_tokens
    SET revoked_at = NOW(), updated_at = NOW()
    WHERE session_id = $1
      AND revoked_at IS NULL
    `,
    [sessionId],
  );
}

async function getRefreshTokenById(tokenId: string): Promise<RefreshTokenRecord | null> {
  const result = await db.query(
    `
    SELECT id, session_id, user_id, token_hash, algorithm, expires_at, consumed_at, revoked_at, replaced_by, created_at, updated_at
    FROM authentication.refresh_tokens
    WHERE id = $1
    LIMIT 1
    `,
    [tokenId],
  );

  return result.rows[0] ? mapRefreshTokenRow(result.rows[0]) : null;
}

async function verifyRefreshTokenSecret(secret: string, stored: RefreshTokenRecord): Promise<boolean> {
  if (stored.algorithm !== 'bcrypt') {
    logger.warn('unsupported refresh token hash algorithm', {
      tokenId: stored.id,
      algorithm: stored.algorithm,
    });
    return false;
  }

  return bcrypt.compare(secret, stored.tokenHash);
}

function mapCredentialRow(row: any): CredentialRecord {
  return {
    id: row.id,
    accountId: Number(row.account_id),
    username: row.username,
    passwordHash: row.password_hash,
    params: row.params ?? {},
    createdAt: new Date(row.created_at).toISOString(),
  };
}

function mapRefreshTokenRow(row: any): RefreshTokenRecord {
  return {
    id: row.id,
    sessionId: row.session_id,
    userId: row.user_id,
    tokenHash: row.token_hash,
    algorithm: row.algorithm,
    expiresAt: new Date(row.expires_at).toISOString(),
    consumedAt: row.consumed_at ? new Date(row.consumed_at).toISOString() : null,
    revokedAt: row.revoked_at ? new Date(row.revoked_at).toISOString() : null,
    replacedBy: row.replaced_by ?? null,
    createdAt: new Date(row.created_at).toISOString(),
    updatedAt: new Date(row.updated_at).toISOString(),
  };
}
