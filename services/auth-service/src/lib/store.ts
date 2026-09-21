import { db } from './db';
import bcrypt from 'bcrypt';

export type UserRecord = {
  id: string;
  username: string;
  passwordHash: string;
  accountId: number;
  roles: string[];
  createdOn: string;
};

/**
 * Check whether the trading account already exists.
 *
 * Registration must link the user to an existing account.
 * It must NOT create a trading account.
 */
export async function accountExists(
  accountId: number,
): Promise<boolean> {
  const result = await db.query(
    `
    SELECT 1
    FROM accounts
    WHERE id = $1
    LIMIT 1
    `,
    [accountId],
  );

  return result.rowCount === 1;
}

/**
 * Check whether a username is already registered.
 */
export async function usernameExists(
  username: string,
): Promise<boolean> {
  const result = await db.query(
    `
    SELECT 1
    FROM users
    WHERE username = $1
    LIMIT 1
    `,
    [username],
  );

  return result.rowCount === 1;
}

/**
 * Find a user by username.
 *
 * password_hash is included here because the login/
 * credential layer needs it for password verification.
 */
export async function findUserByUsername(
  username: string,
): Promise<UserRecord | null> {
  const result = await db.query(
    `
    SELECT
      id,
      username,
      password_hash,
      account_id,
      roles,
      created_on
    FROM users
    WHERE username = $1
    LIMIT 1
    `,
    [username],
  );

  if (result.rows.length === 0) {
    return null;
  }

  return mapUserRow(result.rows[0]);
}

/**
 * Find a user by UUID.
 *
 * This will be used by /auth/me when the
 * guard story is implemented.
 */
export async function findUserById(
  id: string,
): Promise<UserRecord | null> {
  const result = await db.query(
    `
    SELECT
      id,
      username,
      password_hash,
      account_id,
      roles,
      created_on
    FROM users
    WHERE id = $1
    LIMIT 1
    `,
    [id],
  );

  if (result.rows.length === 0) {
    return null;
  }

  return mapUserRow(result.rows[0]);
}

/**
 * Create a user linked to an existing trading account.
 *
 * The passwordHash is supplied by the credential/password
 * component. This function does not perform password hashing.
 *
 * Public registration always creates a CUSTOMER.
 */
export async function createUser(
  username: string,
  passwordHash: string,
  accountId: number,
): Promise<UserRecord> {
  const result = await db.query(
    `
    INSERT INTO users (
      id,
      username,
      password_hash,
      account_id,
      roles,
      created_on
    )
    VALUES (
      gen_random_uuid(),
      $1,
      $2,
      $3,
      ARRAY['CUSTOMER']::TEXT[],
      NOW()
    )
    RETURNING
      id,
      username,
      password_hash,
      account_id,
      roles,
      created_on
    `,
    [
      username,
      passwordHash,
      accountId,
    ],
  );

  return mapUserRow(result.rows[0]);
}

function mapUserRow(row: any): UserRecord {
  return {
    id: row.id,
    username: row.username,
    passwordHash: row.password_hash,
    accountId: Number(row.account_id),
    roles: row.roles ?? ['CUSTOMER'],
    createdOn: new Date(row.created_on).toISOString(),
  };
}

export async function verifyPassword(
  user: UserRecord,
  password: string,
): Promise<boolean> {
  return password===(user.passwordHash);
}

const refreshTokens = new Map<string, string>();

export function saveRefreshToken(
  refreshToken: string,
  userId: string,
): void {
  refreshTokens.set(refreshToken, userId);
}

export function getUserIdByRefreshToken(
  refreshToken: string,
): string | null {
  return refreshTokens.get(refreshToken) ?? null;
}

export function consumeRefreshToken(
  refreshToken: string,
): string | null {
  const userId = refreshTokens.get(refreshToken);

  if (!userId) {
    return null;
  }

  // Rotation: invalidate the old refresh token
  refreshTokens.delete(refreshToken);

  return userId;
}

export function revokeAllRefreshTokensForUser(
  userId: string,
): void {
  for (const [token, storedUserId] of refreshTokens.entries()) {
    if (storedUserId === userId) {
      refreshTokens.delete(token);
    }
  }
}