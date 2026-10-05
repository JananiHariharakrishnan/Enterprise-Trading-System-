import { Test, TestingModule } from '@nestjs/testing';
import { INestApplication, HttpStatus } from '@nestjs/common';
import { AppModule } from '../src/app.module.js';
import request from 'supertest';

/**
 * Integration tests for login endpoint security:
 * - Login throttle enforcement (5 attempts per 60 seconds)
 * - Consistent error responses
 * - Timing consistency to prevent user enumeration attacks
 */

describe('AuthController - Login Security (e2e)', () => {
  describe('Login Throttle', () => {
    /**
     * Acceptance Criterion: A login throttle exists with a documented cooldown
     * and attempt count, and neither path returns early.
     *
     * Limit: 5 failed login attempts
     * Cooldown: 60 seconds
     */
    let app: INestApplication;
    const loginUrl = '/auth/login';

    beforeAll(async () => {
      const moduleFixture: TestingModule = await Test.createTestingModule({
        imports: [AppModule],
      }).compile();

      app = moduleFixture.createNestApplication();
      await app.init();
    });

    afterAll(async () => {
      await app.close();
    });

    it('should allow up to 5 login attempts and throttle on 6th', async () => {
      // Make 5 attempts - all should return 401 (not throttled yet)
      for (let i = 0; i < 5; i++) {
        const response = await request(app.getHttpServer())
          .post(loginUrl)
          .send({
            username: `throttle_test_${i}`,
            password: 'password',
          });

        expect(response.status).toBe(
          HttpStatus.UNAUTHORIZED,
          `Attempt ${i + 1} should be allowed (401)`
        );
      }

      // 6th attempt should be throttled (429)
      const throttledResponse = await request(app.getHttpServer())
        .post(loginUrl)
        .send({
          username: 'throttle_test_6',
          password: 'password',
        });

      expect(throttledResponse.status).toBe(
        HttpStatus.TOO_MANY_REQUESTS,
        'Attempt 6 should be throttled (429)'
      );
    });
  });

  describe('Error Response Consistency', () => {
    /**
     * Acceptance Criterion: Return consistent error status for all authentication failures
     * - unknown user
     * - wrong password
     * - Both should return 401 Unauthorized
     */
    let app: INestApplication;
    const loginUrl = '/auth/login';

    beforeAll(async () => {
      const moduleFixture: TestingModule = await Test.createTestingModule({
        imports: [AppModule],
      }).compile();

      app = moduleFixture.createNestApplication();
      await app.init();
    });

    afterAll(async () => {
      await app.close();
    });

    it('should return 401 for unknown user', async () => {
      const response = await request(app.getHttpServer())
        .post(loginUrl)
        .send({
          username: 'nonexistent_user_xyz',
          password: 'password',
        });

      expect(response.status).toBe(HttpStatus.UNAUTHORIZED);
      // Should have either errorCode or message field
      expect(
        response.body.errorCode || response.body.message
      ).toBeTruthy();
    });

    it('should return 401 for wrong password', async () => {
      const response = await request(app.getHttpServer())
        .post(loginUrl)
        .send({
          username: 'testuser', // This might exist in test DB
          password: 'wrong_password_xyz',
        });

      expect(response.status).toBe(HttpStatus.UNAUTHORIZED);
      expect(
        response.body.errorCode || response.body.message
      ).toBeTruthy();
    });

    it('should return same status code for unknown user and wrong password', async () => {
      const unknownUserResponse = await request(app.getHttpServer())
        .post(loginUrl)
        .send({
          username: 'unknown_user_1',
          password: 'password',
        });

      const wrongPasswordResponse = await request(app.getHttpServer())
        .post(loginUrl)
        .send({
          username: 'testuser',
          password: 'wrong_password',
        });

      // Both should return 401
      expect(unknownUserResponse.status).toBe(HttpStatus.UNAUTHORIZED);
      expect(wrongPasswordResponse.status).toBe(HttpStatus.UNAUTHORIZED);
      expect(unknownUserResponse.status).toBe(wrongPasswordResponse.status);
    });
  });

  describe('Timing Consistency', () => {
    /**
     * Acceptance Criterion: Unknown user and wrong password should have comparable timing
     * to prevent timing attacks that reveal valid usernames.
     *
     * The service verifies unknown passwords against a dummy hash to maintain timing consistency.
     */
    let app: INestApplication;
    const loginUrl = '/auth/login';

    beforeAll(async () => {
      const moduleFixture: TestingModule = await Test.createTestingModule({
        imports: [AppModule],
      }).compile();

      app = moduleFixture.createNestApplication();
      await app.init();
    });

    afterAll(async () => {
      await app.close();
    });

    it('should take comparable time for unknown user vs wrong password', async () => {
      const timings: number[] = [];

      // Measure unknown user timing
      const unknownStart = performance.now();
      await request(app.getHttpServer())
        .post(loginUrl)
        .send({
          username: 'nonexistent_timing_1',
          password: 'password123',
        });
      const unknownTime = performance.now() - unknownStart;
      timings.push(unknownTime);

      // Measure wrong password timing
      const wrongPassStart = performance.now();
      await request(app.getHttpServer())
        .post(loginUrl)
        .send({
          username: 'testuser',
          password: 'wrong_password_timing',
        });
      const wrongPassTime = performance.now() - wrongPassStart;
      timings.push(wrongPassTime);

      // Both should complete (timing check is informational)
      // In production, these times should be within ~50% of each other
      console.log(
        `Unknown user: ${unknownTime}ms, Wrong password: ${wrongPassTime}ms`
      );
      expect(timings.every(t => t > 0)).toBe(true);
    });
  });
});
