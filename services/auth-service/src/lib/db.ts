import { Pool } from 'pg';
import { v4 as uuidv4 } from 'uuid';
import { loadEnvConfig } from '@next/env';
import path from 'node:path';
import fs from 'node:fs';

function loadAuthServiceEnv(): void {
    const cwd = process.cwd();
    const authServiceDir = path.basename(cwd) === 'auth-service'
        ? cwd
        : path.resolve(cwd, 'services', 'auth-service');

    const candidateDirs = Array.from(new Set([authServiceDir, cwd]));

    for (const dir of candidateDirs) {
        const hasEnvFile =
            fs.existsSync(path.join(dir, '.env.local')) ||
            fs.existsSync(path.join(dir, '.env'));

        if (hasEnvFile) {
            loadEnvConfig(dir);
            return;
        }
    }
}

if (process.env.NODE_ENV !== 'test') {
    loadAuthServiceEnv();
}

let db: any;

if (process.env.NODE_ENV === 'test') {
    type Row = any;
    const credentials: Row[] = [];

    db = {
        async query(sql: string, params?: any[]) {
            const s = (sql || '').trim().toUpperCase();

            if (s.startsWith('INSERT INTO CREDENTIALS')) {
                const row = {
                    id: uuidv4(),
                    account_id: Number(params?.[0]),
                    username: params?.[1],
                    password_hash: params?.[2],
                    algorithm: params?.[3],
                    params: params?.[4] ? JSON.parse(params[4]) : null,
                    created_at: new Date().toISOString(),
                    updated_at: new Date().toISOString(),
                };
                credentials.push(row);
                return { rows: [row] } as any;
            }

            if (s.startsWith('UPDATE CREDENTIALS')) {

                const newHash = params?.[0];
                const newAlg = params?.[1];
                const newParams = params?.[2] ? JSON.parse(params[2]) : null;
                const username = params?.[3];
                const found = credentials.find((r) => r.username === username);
                if (found) {
                    found.password_hash = newHash ?? found.password_hash;
                    found.algorithm = newAlg ?? found.algorithm;
                    found.params = newParams ?? found.params;
                    found.updated_at = new Date().toISOString();
                    return { rows: [found] } as any;
                }
                return { rows: [] } as any;
            }

            if (s.startsWith('SELECT * FROM CREDENTIALS')) {
                const username = params?.[0];
                const found = credentials.find((r) => r.username === username);
                return { rows: found ? [found] : [] } as any;
            }
            return { rows: [] } as any;
        },
    } as const;

} else {
    const rawDatabaseUrl = process.env.DATABASE_URL ?? process.env.DB_URL;

    const databaseUrl = rawDatabaseUrl && rawDatabaseUrl.startsWith('jdbc:')
        ? rawDatabaseUrl.replace(/^jdbc:/, '')
        : rawDatabaseUrl;

    const fallbackHost =
        process.env.DB_HOST ??
        process.env.POSTGRES_HOST ??
        process.env.DATABASE_HOST ??
        process.env.PGHOST ??
        'localhost';
    const fallbackPort = Number(
        process.env.DB_PORT ??
        process.env.POSTGRES_PORT ??
        process.env.DATABASE_PORT ??
        process.env.PGPORT ??
        5432,
    );
    const fallbackDatabase =
        process.env.DB_NAME ??
        process.env.POSTGRES_DB ??
        process.env.DATABASE_NAME ??
        process.env.PGDATABASE ??
        'trading';
    const fallbackUser =
        process.env.DB_USER ??
        process.env.POSTGRES_USER ??
        process.env.DB_USERNAME ??
        process.env.DATABASE_USER ??
        process.env.PGUSER;
    const fallbackPassword =
        process.env.DB_PASSWORD ??
        process.env.POSTGRES_PASSWORD ??
        process.env.DATABASE_PASSWORD ??
        process.env.PGPASSWORD;

    let connectionString = databaseUrl;
    if (connectionString) {
        try {
            const url = new URL(connectionString);
            if (!url.username && typeof fallbackUser === 'string' && fallbackUser.length > 0) {
                url.username = fallbackUser;
            }
            if (!url.password && typeof fallbackPassword === 'string' && fallbackPassword.length > 0) {
                url.password = fallbackPassword;
            }
            connectionString = url.toString();
        } catch {
        }
    }

    const poolConfig = connectionString
            ? { connectionString }
            : {
                        host: fallbackHost,
                        port: fallbackPort,
                        database: fallbackDatabase,
                        user: fallbackUser,
                        password: fallbackPassword,
            };

    // Fail early with an actionable message instead of a pg SCRAM type error.
    if (!connectionString) {
        if (typeof fallbackUser !== 'string' || fallbackUser.length === 0) {
            throw new Error(
                'Database configuration error: missing DB user. Set DB_USER (or POSTGRES_USER).',
            );
        }

        if (typeof fallbackPassword !== 'string' || fallbackPassword.length === 0) {
            throw new Error(
                'Database configuration error: missing DB password. Set DB_PASSWORD (or POSTGRES_PASSWORD).',
            );
        }
    } else {
        try {
            const url = new URL(connectionString);
            const hasUser = typeof url.username === 'string' && url.username.length > 0;
            const hasPassword = typeof url.password === 'string' && url.password.length > 0;

            if (!hasUser) {
                throw new Error(
                    'Database configuration error: URL is missing DB user. Set DATABASE_URL with user or set DB_USER/POSTGRES_USER.',
                );
            }

            if (!hasPassword) {
                throw new Error(
                    'Database configuration error: URL is missing DB password. Set DATABASE_URL with password or set DB_PASSWORD/POSTGRES_PASSWORD.',
                );
            }
        } catch (error) {
            if (error instanceof Error && error.message.startsWith('Database configuration error:')) {
                throw error;
            }

        }
    }

    db = new Pool(poolConfig);
}
export { db };