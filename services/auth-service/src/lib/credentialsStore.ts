import { db } from './db';
import argon2 from 'argon2';
import bcrypt from 'bcrypt';
import { redact, logger } from './logger';

const PG_UNDEFINED_COLUMN = '42703';

function isMissingColumnError(error: unknown, columnName: string): boolean {
  if (!error || typeof error !== 'object') return false;
  const maybe = error as { code?: unknown; message?: unknown };
  const message = typeof maybe.message === 'string' ? maybe.message.toLowerCase() : '';
  return (
    maybe.code === PG_UNDEFINED_COLUMN &&
    message.includes(`column "${columnName.toLowerCase()}"`) 
  );
}

async function queryCredentialsPasswordColumn(
  builder: (columnName: 'password_hash' | 'password') => string,
  params: any[],
): Promise<any> {
  const candidates: Array<'password_hash' | 'password'> = ['password_hash', 'password'];
  let lastError: unknown;

  for (const columnName of candidates) {
    try {
      return await db.query(builder(columnName), params);
    } catch (error) {
      if (isMissingColumnError(error, columnName)) {
        lastError = error;
        continue;
      }
      throw error;
    }
  }

  throw lastError ?? new Error('Database configuration error: credentials password column not found.');
}

async function updateUsersPasswordByUsername(
  username: string,
  hash: string,
): Promise<void> {
  const candidates: Array<'password_hash' | 'password'> = ['password_hash', 'password'];
  let sawMissingColumn = false;

  for (const columnName of candidates) {
    try {
      await db.query(
        `UPDATE users SET ${columnName} = $1 WHERE username = $2`,
        [hash, username],
      );
      return;
    } catch (error) {
      if (isMissingColumnError(error, columnName)) {
        sawMissingColumn = true;
        continue;
      }
      throw error;
    }
  }

  // Older schemas store auth data only in credentials. Do not fail login/register
  // if users table has neither password_hash nor password column.
  if (sawMissingColumn) {
    logger.warn('users password column missing; skipped users hash backfill', redact({ username }));
    return;
  }

  throw new Error('Database configuration error: users password column not found.');
}
export type CredentialRow = {
  id: string;
  account_id: number;
  username: string;
  password_hash: string;
  algorithm: string;
  params: any;
  created_at: string;
};

const DEFAULT_ARGON2_OPTIONS = {
  type: argon2.argon2id,
  timeCost: Number(process.env.ARGON2_TIME ?? 3),
  memoryCost: Number(process.env.ARGON2_MEMORY_KB ?? 32768),
  parallelism: Number(process.env.ARGON2_PARALLELISM ?? 1),
};

function isArgon2Encoded(hash: unknown): hash is string {
  return typeof hash === 'string' && hash.startsWith('$argon2');
}

function extractShortFromEncoded(encoded: string): string {
  const parts = encoded.split('$');
  return parts.slice(-2).join('$');
}

function buildEncodedFromShort(short: string, algorithm: string | undefined, params: any): string {
  const alg = (algorithm || 'argon2id').toLowerCase();
  const time = params?.time ?? DEFAULT_ARGON2_OPTIONS.timeCost;
  const memory = params?.memory_kb ?? DEFAULT_ARGON2_OPTIONS.memoryCost;
  const parallelism = params?.parallelism ?? DEFAULT_ARGON2_OPTIONS.parallelism;
  return `$${alg}$v=19$m=${memory},t=${time},p=${parallelism}$${short}`;
}

export async function createCredential(
  accountId: number,
  username: string,
  plainPassword: string,
): Promise<CredentialRow> {
  // Ensure the password is a string. Clients may send passwords containing
  // arbitrary characters; coerce to string to avoid downstream errors from
  // libraries that expect string inputs (e.g., SASL/argon2). This preserves
  // characters while preventing non-string types from causing crashes.
  const passwordStr = typeof plainPassword === 'string' ? plainPassword : String(plainPassword);

  const fullHash = await argon2.hash(passwordStr, DEFAULT_ARGON2_OPTIONS);
  const hash = fullHash;

  const result = await queryCredentialsPasswordColumn(
    (passwordColumn) => `INSERT INTO credentials (
        id,
        account_id,
        username,
        ${passwordColumn},
        algorithm,
        params,
        created_at,
        updated_at
      ) VALUES (
        gen_random_uuid(), $1, $2, $3, $4, $5::jsonb, NOW(), NOW()
      ) RETURNING *`,
    [accountId, username, hash, 'argon2id', JSON.stringify({
      time: DEFAULT_ARGON2_OPTIONS.timeCost,
      memory_kb: DEFAULT_ARGON2_OPTIONS.memoryCost,
      parallelism: DEFAULT_ARGON2_OPTIONS.parallelism,
    })],
  );

  return mapRow(result.rows[0]);
}

export async function findCredentialByUsername(
  username: string,
): Promise<CredentialRow | null> {
  const result = await db.query(
    `SELECT * FROM credentials WHERE username = $1 LIMIT 1`,
    [username],
  );

  if (result.rows.length === 0) return null;
  return mapRow(result.rows[0]);
}
export async function verifyPassword(
    stored: CredentialRow | null,
    suppliedPassword: string,
): Promise<boolean> {
    if (!stored) {
        // perform a dummy verify to equalise timing (argon2 dummy hash)
        try {
            await argon2.verify(
                '$argon2id$v=19$m=65536,t=3,p=1$AAAAAAAAAAAAAAAAAAAAAA$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA',
                suppliedPassword,
            );
        } catch (e) {
            // ignore
        }
        return false;
    }

    const alg = (stored.algorithm || '').toLowerCase();

    // Prefer argon2id (allow 'argon2' as synonym)
    if (alg === 'argon2id' || alg === 'argon2') {
      try {
        const storedHash = stored.password_hash;
        let fullEncoded: string;

        if (isArgon2Encoded(storedHash)) {
          fullEncoded = storedHash;
        } else {
          fullEncoded = buildEncodedFromShort(String(storedHash), stored.algorithm, stored.params);
        }

        return await argon2.verify(fullEncoded, suppliedPassword);
      } catch (e) {
        logger.error('password verify failed', redact({
          username: stored.username,
          algorithm: stored.algorithm,
          message: (e as Error)?.message,
        }));
        return false;
      }
    }

    // Support legacy bcrypt rows but warn and recommend migration
    if (alg === 'bcrypt' || alg === 'bcryptjs') {
        logger.warn('bcrypt used for password verification — consider migrating to argon2id', redact({
            username: stored.username,
        }));
        try {
        const ok = await bcrypt.compare(suppliedPassword, stored.password_hash);

        // If bcrypt verification succeeds, migrate this credential to argon2id.
        // Store only the argon2 hash in the `password_hash` column (params go to the
        // separate `params` JSONB column). Also update the users table password
        // hash so both places use argon2.
        if (ok) {
          try {
            const newFullHash = await argon2.hash(suppliedPassword, DEFAULT_ARGON2_OPTIONS);
            const params = JSON.stringify({
              time: DEFAULT_ARGON2_OPTIONS.timeCost,
              memory_kb: DEFAULT_ARGON2_OPTIONS.memoryCost,
              parallelism: DEFAULT_ARGON2_OPTIONS.parallelism,
            });

            // Update credentials row (store full encoded form)
            await queryCredentialsPasswordColumn(
              (passwordColumn) =>
                `UPDATE credentials SET ${passwordColumn} = $1, algorithm = $2, params = $3::jsonb, updated_at = NOW() WHERE username = $4`,
              [newFullHash, 'argon2id', params, stored.username],
            );

            // Also update the users table password_hash if present (store full encoded form)
            await updateUsersPasswordByUsername(stored.username, newFullHash);
          } catch (e) {
            logger.error('failed to migrate bcrypt credential to argon2', redact({
              username: stored.username,
              message: (e as Error)?.message,
            }));
          }
        }

        return ok;
        } catch (e) {
            logger.error('bcrypt verify failed', redact({
                username: stored.username,
                algorithm: stored.algorithm,
                message: (e as Error)?.message,
            }));
            return false;
        }
    }

    // Explicit refusal for insecure or unknown algorithms
    logger.warn('refusing to verify password using insecure or unknown algorithm', redact({
        username: stored.username,
        algorithm: stored.algorithm,
    }));
    return false;
}

function mapRow(row: any): CredentialRow {
  return {
    id: row.id,
    account_id: Number(row.account_id),
    username: row.username,
    password_hash: row.password_hash ?? row.password,
    algorithm: row.algorithm,
    params: row.params,
    created_at: row.created_at,
  };
}
