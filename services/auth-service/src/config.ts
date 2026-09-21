export interface DatabaseConfig {
  url?: string;
  host: string;
  port: number;
  name: string;
  user: string;
  password: string;
}

export interface AuthServiceConfig {
  nodeEnv: string;
  port: number;
  jwtSecret: string;
  jwtIssuer: string;
  database: DatabaseConfig;
}

function parseInteger(value: string | undefined, fallback: number, envName: string): number {
  if (value === undefined || value.trim() === '') {
    return fallback;
  }

  const parsed = Number.parseInt(value, 10);
  if (!Number.isFinite(parsed)) {
    throw new TypeError(`${envName} must be a valid integer`);
  }

  return parsed;
}

export function loadConfig(env: NodeJS.ProcessEnv = process.env): AuthServiceConfig {
  return {
    nodeEnv: env.NODE_ENV ?? 'development',
    port: parseInteger(env.PORT ?? env.SERVER_PORT, 3000, 'PORT'),
    jwtSecret: env.JWT_SECRET ?? '',
    jwtIssuer: env.JWT_ISSUER ?? 'auth-service',
    database: {
      url: env.DATABASE_URL || undefined,
      host: env.POSTGRES_HOST ?? 'localhost',
      port: parseInteger(env.POSTGRES_PORT, 5432, 'POSTGRES_PORT'),
      name: env.POSTGRES_DB ?? 'trading',
      user: env.POSTGRES_USER ?? 'postgres',
      password: env.POSTGRES_PASSWORD ?? ''
    }
  };
}

