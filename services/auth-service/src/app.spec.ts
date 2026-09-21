import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import request from 'supertest';
import { createApp } from './app';
import { AuthServiceConfig } from './config';

const config: AuthServiceConfig = {
  nodeEnv: 'test',
  port: 3000,
  jwtSecret: 'test-secret',
  jwtIssuer: 'auth-service',
  database: {
    url: undefined,
    host: 'localhost',
    port: 5432,
    name: 'trading',
    user: 'postgres',
    password: 'postgres'
  }
};

describe('createApp', () => {
  it('returns service health', async () => {
    const response = await request(createApp(config)).get('/health');

    assert.equal(response.status, 200);
    assert.deepEqual(response.body, {
      status: 'ok',
      service: 'auth-service'
    });
  });

  it('keeps login endpoint reserved for future implementation', async () => {
    const response = await request(createApp(config)).post('/auth/login').send({
      username: 'demo',
      password: 'secret'
    });

    assert.equal(response.status, 501);
    assert.deepEqual(response.body, {
      errorCode: 'AUTH-501',
      message: 'Not implemented yet'
    });
  });
});

