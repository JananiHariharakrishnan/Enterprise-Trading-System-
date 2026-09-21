import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { loadConfig } from './config';

describe('loadConfig', () => {
  it('uses repo-friendly defaults', () => {
    const config = loadConfig({});

    assert.deepEqual(config, {
      nodeEnv: 'development',
      port: 3000,
      jwtSecret: '',
      jwtIssuer: 'auth-service',
      database: {
        url: undefined,
        host: 'localhost',
        port: 5432,
        name: 'trading',
        user: 'postgres',
        password: ''
      }
    });
  });

  it('reads overrides from environment variables', () => {
    const config = loadConfig({
      NODE_ENV: 'test',
      PORT: '3100',
      JWT_SECRET: 'secret',
      JWT_ISSUER: 'issuer',
      DATABASE_URL: 'postgresql://user:pass@db:5432/trading',
      POSTGRES_HOST: 'db',
      POSTGRES_PORT: '5433',
      POSTGRES_DB: 'trading_dev',
      POSTGRES_USER: 'service_user',
      POSTGRES_PASSWORD: 'service_password'
    });

    assert.deepEqual(config, {
      nodeEnv: 'test',
      port: 3100,
      jwtSecret: 'secret',
      jwtIssuer: 'issuer',
      database: {
        url: 'postgresql://user:pass@db:5432/trading',
        host: 'db',
        port: 5433,
        name: 'trading_dev',
        user: 'service_user',
        password: 'service_password'
      }
    });
  });

  it('rejects an invalid port value', () => {
    assert.throws(() => loadConfig({ PORT: 'not-a-number' }), new TypeError('PORT must be a valid integer'));
  });
});

