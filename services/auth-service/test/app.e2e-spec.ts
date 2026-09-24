import { INestApplication } from '@nestjs/common';
import { Test, TestingModule } from '@nestjs/testing';
import request from 'supertest';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

async function createApp(): Promise<INestApplication> {
  const { AppModule } = await import('../src/app.module.js');
  const { configureApp } = await import('../src/app/configure-app.js');
  const moduleFixture: TestingModule = await Test.createTestingModule({
    imports: [AppModule],
  }).compile();

  const app = moduleFixture.createNestApplication();
  configureApp(app);
  await app.init();
  return app;
}

describe('auth contract (e2e)', () => {
  let app: INestApplication | undefined;

  beforeEach(async () => {
    vi.resetModules();
    process.env.JWT_SECRET = 'e2e-secret';
    process.env.NODE_ENV = 'test';
    app = await createApp();
  }, 20_000);

  it('implements registration, login, refresh and profile', async () => {
    const registration = await request(app!.getHttpServer())
      .post('/auth/register')
      .send({ username: 'priya.menon', password: 'correct horse battery staple', accountId: 1 })
      .expect(201);

    expect(registration.body).toEqual({
      id: expect.any(String),
      username: 'priya.menon',
      accountId: 1,
      roles: ['CUSTOMER'],
      createdOn: expect.any(String),
    });
    expect(registration.body).not.toHaveProperty('accessToken');

    await request(app!.getHttpServer())
      .post('/auth/register')
      .send({ username: 'priya.menon', password: 'correct horse battery staple', accountId: 1 })
      .expect(409)
      .expect({ errorCode: 'AUTH-409', message: 'Registration failed' });

    const login = await request(app!.getHttpServer())
      .post('/auth/login')
      .send({ username: 'priya.menon', password: 'correct horse battery staple' })
      .expect(200);

    expect(login.body).toEqual({
      accessToken: expect.any(String),
      refreshToken: expect.any(String),
      tokenType: 'Bearer',
      expiresIn: 900,
    });

    await request(app!.getHttpServer())
      .get('/auth/me')
      .set('Authorization', `Bearer ${login.body.accessToken}`)
      .expect(200)
      .expect({
        id: registration.body.id,
        username: 'priya.menon',
        accountId: 1,
        firstName: null,
        lastName: null,
        email: null,
        phoneNo: null,
        roles: ['CUSTOMER'],
        createdOn: registration.body.createdOn,
      });

    const refreshed = await request(app!.getHttpServer())
      .post('/auth/refresh')
      .send({ refreshToken: login.body.refreshToken })
      .expect(200);

    expect(refreshed.body).toEqual({
      accessToken: expect.any(String),
      refreshToken: expect.any(String),
      tokenType: 'Bearer',
      expiresIn: 900,
    });
    expect(refreshed.body.refreshToken).not.toBe(login.body.refreshToken);
    expect(refreshed.body.accessToken).not.toBe(login.body.accessToken);

    const refreshedAgain = await request(app!.getHttpServer())
      .post('/auth/refresh')
      .send({ refreshToken: refreshed.body.refreshToken })
      .expect(200);

    expect(refreshedAgain.body.refreshToken).not.toBe(refreshed.body.refreshToken);
    expect(refreshedAgain.body.accessToken).not.toBe(refreshed.body.accessToken);

    await request(app!.getHttpServer())
      .post('/auth/refresh')
      .send({ refreshToken: login.body.refreshToken })
      .expect(401)
      .expect({ errorCode: 'AUTH-401', message: 'Unauthorised' });

    await request(app!.getHttpServer())
      .post('/auth/refresh')
      .send({ refreshToken: refreshedAgain.body.refreshToken })
      .expect(401)
      .expect({ errorCode: 'AUTH-401', message: 'Unauthorised' });
  });

  it('returns only platform envelopes on failure', async () => {
    const invalidRegistration = await request(app!.getHttpServer())
      .post('/auth/register')
      .send({ username: 'ab', password: 'short', accountId: 0 })
      .expect(422);

    expect(invalidRegistration.body.errorCode).toBe('VAL-422');
    expect(invalidRegistration.body.message).toContain('Invalid input:');
    expect(invalidRegistration.body.message).toContain('username must be longer than or equal to 3 characters');
    expect(invalidRegistration.body.message).toContain('password must be longer than or equal to 12 characters');
    expect(invalidRegistration.body.message).toContain('accountId must not be less than 1');

    await request(app!.getHttpServer())
      .post('/auth/login')
      .send({ username: 'unknown.user', password: 'correct horse battery staple' })
      .expect(401)
      .expect({ errorCode: 'AUTH-401', message: 'Unauthorised' });

    await request(app!.getHttpServer())
      .get('/auth/me')
      .set('Authorization', 'Bearer malformed')
      .expect(401)
      .expect({ errorCode: 'AUTH-401', message: 'Unauthorised' });
  });

  afterEach(async () => {
    if (app) {
      await app.close();
    }
  });
});
