import dotenv from 'dotenv';
import fs from 'node:fs';
import path from 'node:path';
import { Pool } from 'pg';
import { v4 as uuidv4 } from 'uuid';

function loadEnvFiles(): void {
  const cwd = process.cwd();
  const candidates = [path.join(cwd, '.env.local'), path.join(cwd, '.env')];

  for (const candidate of candidates) {
    if (fs.existsSync(candidate)) {
      dotenv.config({ path: candidate });
      return;
    }
  }

  dotenv.config();
}

const isTest = process.env.NODE_ENV === 'test' || Boolean(process.env.VITEST_POOL_ID) || process.env.VITEST === 'true';

if (!isTest) {
  loadEnvFiles();
}

type QueryResult = { rows: any[]; rowCount: number };

function createInMemoryDb() {
  const accounts = [{ id: 1, account_reference: 'AUTH-TEST-001', cash_balance: 10000, buying_power: 10000, status: 'ACTIVE' }];
  const users: any[] = [];
  const credentials: any[] = [];
  const refreshTokens: any[] = [];

  return {
    async query(sql: string, params: any[] = []): Promise<QueryResult> {
      const normalized = sql.replace(/\s+/g, ' ').trim().toUpperCase();

      if (normalized.includes('FROM ACCOUNTS') && normalized.includes('WHERE ID = $1')) {
        const found = accounts.find((row) => Number(row.id) === Number(params[0]));
        return { rows: found ? [{ '?column?': 1 }] : [], rowCount: found ? 1 : 0 };
      }

      if (normalized.startsWith('SELECT ID, USERNAME') && normalized.includes('FROM USERS') && normalized.includes('WHERE USERNAME = $1')) {
        const found = users.find((row) => row.username === params[0]);
        return { rows: found ? [found] : [], rowCount: found ? 1 : 0 };
      }

      if (normalized.startsWith('SELECT ID, USERNAME') && normalized.includes('FROM USERS') && normalized.includes('WHERE ID = $1')) {
        const found = users.find((row) => row.id === params[0]);
        return { rows: found ? [found] : [], rowCount: found ? 1 : 0 };
      }

      if (normalized.startsWith('SELECT ID, USERNAME') && normalized.includes('FROM USERS') && normalized.includes('WHERE ACCOUNT_ID = $1')) {
        const found = users.find((row) => Number(row.account_id) === Number(params[0]));
        return { rows: found ? [found] : [], rowCount: found ? 1 : 0 };
      }

      if (normalized.startsWith('INSERT INTO USERS')) {
        const row = {
          id: uuidv4(),
          username: params[0],
          first_name: null,
          last_name: null,
          email: null,
          phone_no: null,
          account_id: Number(params[1]),
          roles: params[2] ?? ['CUSTOMER'],
          created_on: new Date().toISOString(),
          password_hash: null,
        };
        users.push(row);
        return { rows: [row], rowCount: 1 };
      }

      if (normalized.startsWith('INSERT INTO CREDENTIALS')) {
        const row = {
          id: uuidv4(),
          account_id: Number(params[0]),
          username: params[1],
          password_hash: params[2],
          algorithm: params[3],
          params: JSON.parse(params[4]),
          created_at: new Date().toISOString(),
          updated_at: new Date().toISOString(),
        };
        credentials.push(row);
        return { rows: [row], rowCount: 1 };
      }

      if (normalized.startsWith('SELECT * FROM CREDENTIALS')) {
        const found = credentials.find((row) => row.username === params[0]);
        return { rows: found ? [found] : [], rowCount: found ? 1 : 0 };
      }

      if (normalized.startsWith('INSERT INTO REFRESH_TOKENS')) {
        const row = {
          id: params[0],
          session_id: params[1],
          user_id: params[2],
          token_hash: params[3],
          algorithm: params[4],
          expires_at: params[5],
          consumed_at: null,
          revoked_at: null,
          replaced_by: null,
          created_at: new Date().toISOString(),
          updated_at: new Date().toISOString(),
        };
        refreshTokens.push(row);
        return { rows: [row], rowCount: 1 };
      }

      if (normalized.startsWith('SELECT ID, SESSION_ID') && normalized.includes('FROM REFRESH_TOKENS') && normalized.includes('WHERE ID = $1')) {
        const found = refreshTokens.find((row) => row.id === params[0]);
        return { rows: found ? [found] : [], rowCount: found ? 1 : 0 };
      }

      if (normalized.startsWith('UPDATE REFRESH_TOKENS SET CONSUMED_AT = NOW()')) {
        const found = refreshTokens.find((row) => row.id === params[0]);
        if (!found) return { rows: [], rowCount: 0 };
        found.consumed_at = new Date().toISOString();
        found.updated_at = new Date().toISOString();
        return { rows: [found], rowCount: 1 };
      }

      if (normalized.startsWith('UPDATE REFRESH_TOKENS SET REVOKED_AT = NOW()')) {
        const matched = refreshTokens.filter((row) => row.session_id === params[0] && !row.revoked_at);
        for (const row of matched) {
          row.revoked_at = new Date().toISOString();
          row.updated_at = new Date().toISOString();
        }
        return { rows: matched, rowCount: matched.length };
      }

      if (normalized.startsWith('UPDATE REFRESH_TOKENS SET REPLACED_BY = $1')) {
        const found = refreshTokens.find((row) => row.id === params[1]);
        if (!found) return { rows: [], rowCount: 0 };
        found.replaced_by = params[0];
        found.updated_at = new Date().toISOString();
        return { rows: [found], rowCount: 1 };
      }

      return { rows: [], rowCount: 0 };
    },
  };
}

const databaseUrl = process.env.DATABASE_URL ?? process.env.DB_URL;
const host = process.env.POSTGRES_HOST ?? process.env.DB_HOST ?? process.env.PGHOST ?? 'localhost';
const port = Number(process.env.POSTGRES_PORT ?? process.env.DB_PORT ?? process.env.PGPORT ?? 5432);
const database = process.env.POSTGRES_DB ?? process.env.DB_NAME ?? process.env.PGDATABASE ?? 'trading';
const user = process.env.POSTGRES_USER ?? process.env.DB_USER ?? process.env.PGUSER;
const password = process.env.POSTGRES_PASSWORD ?? process.env.DB_PASSWORD ?? process.env.PGPASSWORD;

export const db = isTest
  ? createInMemoryDb()
  : new Pool(
      databaseUrl
        ? { connectionString: databaseUrl.startsWith('jdbc:') ? databaseUrl.replace(/^jdbc:/, '') : databaseUrl }
        : { host, port, database, user, password },
    );

