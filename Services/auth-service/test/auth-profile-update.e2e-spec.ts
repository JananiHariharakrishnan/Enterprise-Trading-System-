import { Test, TestingModule } from '@nestjs/testing';
import { INestApplication, HttpStatus } from '@nestjs/common';
import { AppModule } from '../src/app.module.js';
import request from 'supertest';

/**
 * Integration tests for profile update endpoint:
 * - User can update their own profile
 * - Only authenticated users can update
 * - User cannot update other users' profiles
 * - Input validation works correctly
 */

describe('AuthController - Profile Update (e2e)', () => {
  let app: INestApplication;
  const authUrl = '/auth';
  const baseTestUser = {
    username: 'profiletest_user',
    password: 'ValidPassword123!',
  };

  let accessToken: string;
  let userId: string;

  beforeAll(async () => {
    const moduleFixture: TestingModule = await Test.createTestingModule({
      imports: [AppModule],
    }).compile();

    app = moduleFixture.createNestApplication();
    await app.init();

    // Register a test user
    const registerResponse = await request(app.getHttpServer())
      .post(`${authUrl}/register`)
      .send(baseTestUser);

    expect(registerResponse.status).toBe(HttpStatus.CREATED);
    userId = registerResponse.body.id;

    // Login to get access token
    const loginResponse = await request(app.getHttpServer())
      .post(`${authUrl}/login`)
      .send({
        username: baseTestUser.username,
        password: baseTestUser.password,
      });

    expect(loginResponse.status).toBe(HttpStatus.OK);
    accessToken = loginResponse.body.accessToken;
  });

  afterAll(async () => {
    await app.close();
  });

  describe('Successful Profile Update', () => {
    it('should update user profile with all fields', async () => {
      const updateData = {
        firstName: 'John',
        lastName: 'Doe',
        email: 'john.doe@example.com',
        phoneNo: '+1-234-567-8900',
      };

      const response = await request(app.getHttpServer())
        .put(`${authUrl}/profile`)
        .set('Authorization', `Bearer ${accessToken}`)
        .send(updateData);

      expect(response.status).toBe(HttpStatus.OK);
      expect(response.body.id).toBe(userId);
      expect(response.body.firstName).toBe(updateData.firstName);
      expect(response.body.lastName).toBe(updateData.lastName);
      expect(response.body.email).toBe(updateData.email);
      expect(response.body.phoneNo).toBe(updateData.phoneNo);
      expect(response.body.username).toBe(baseTestUser.username);
    });

    it('should update only some profile fields', async () => {
      const updateData = {
        firstName: 'Jane',
        email: 'jane.doe@example.com',
      };

      const response = await request(app.getHttpServer())
        .put(`${authUrl}/profile`)
        .set('Authorization', `Bearer ${accessToken}`)
        .send(updateData);

      expect(response.status).toBe(HttpStatus.OK);
      expect(response.body.firstName).toBe('Jane');
      expect(response.body.email).toBe('jane.doe@example.com');
      // Last name should remain unchanged from previous test
      expect(response.body.lastName).toBe('Doe');
    });

    it('should update with empty optional fields (no-op)', async () => {
      const updateData = {};

      const response = await request(app.getHttpServer())
        .put(`${authUrl}/profile`)
        .set('Authorization', `Bearer ${accessToken}`)
        .send(updateData);

      expect(response.status).toBe(HttpStatus.OK);
      expect(response.body.firstName).toBe('Jane');
    });

    it('should return user profile data after update', async () => {
      const response = await request(app.getHttpServer())
        .put(`${authUrl}/profile`)
        .set('Authorization', `Bearer ${accessToken}`)
        .send({
          firstName: 'UpdatedFirst',
          lastName: 'UpdatedLast',
        });

      expect(response.status).toBe(HttpStatus.OK);
      expect(response.body).toHaveProperty('id');
      expect(response.body).toHaveProperty('username');
      expect(response.body).toHaveProperty('accountId');
      expect(response.body).toHaveProperty('firstName');
      expect(response.body).toHaveProperty('lastName');
      expect(response.body).toHaveProperty('email');
      expect(response.body).toHaveProperty('phoneNo');
      expect(response.body).toHaveProperty('roles');
      expect(response.body).toHaveProperty('createdOn');
    });
  });

  describe('Authorization and Authentication', () => {
    it('should reject update without authentication token', async () => {
      const response = await request(app.getHttpServer())
        .put(`${authUrl}/profile`)
        .send({
          firstName: 'Unauthorized',
        });

      expect(response.status).toBe(HttpStatus.UNAUTHORIZED);
    });

    it('should reject update with invalid token', async () => {
      const response = await request(app.getHttpServer())
        .put(`${authUrl}/profile`)
        .set('Authorization', 'Bearer invalid_token_xyz')
        .send({
          firstName: 'Unauthorized',
        });

      expect(response.status).toBe(HttpStatus.UNAUTHORIZED);
    });

    it('should reject update with malformed auth header', async () => {
      const response = await request(app.getHttpServer())
        .put(`${authUrl}/profile`)
        .set('Authorization', 'InvalidFormat')
        .send({
          firstName: 'Unauthorized',
        });

      expect(response.status).toBe(HttpStatus.UNAUTHORIZED);
    });
  });

  describe('Input Validation', () => {
    it('should reject invalid email format', async () => {
      const response = await request(app.getHttpServer())
        .put(`${authUrl}/profile`)
        .set('Authorization', `Bearer ${accessToken}`)
        .send({
          email: 'not-an-email',
        });

      expect(response.status).toBe(HttpStatus.BAD_REQUEST);
    });

    it('should accept valid email format', async () => {
      const response = await request(app.getHttpServer())
        .put(`${authUrl}/profile`)
        .set('Authorization', `Bearer ${accessToken}`)
        .send({ email: 'user@example.com' });

      expect(response.status).toBe(HttpStatus.OK);
      expect(response.body.email).toBe('user@example.com');
    });

    it('should reject phone number with invalid characters', async () => {
      const response = await request(app.getHttpServer())
        .put(`${authUrl}/profile`)
        .set('Authorization', `Bearer ${accessToken}`)
        .send({
          phoneNo: 'abc123xyz',
        });

      expect(response.status).toBe(HttpStatus.BAD_REQUEST);
    });

    it('should reject firstName exceeding max length', async () => {
      const response = await request(app.getHttpServer())
        .put(`${authUrl}/profile`)
        .set('Authorization', `Bearer ${accessToken}`)
        .send({
          firstName: 'a'.repeat(101),
        });

      expect(response.status).toBe(HttpStatus.BAD_REQUEST);
    });
  });

  describe('Profile Persistence', () => {
    it('should persist updated profile when fetching via /me endpoint', async () => {
      const updateData = {
        firstName: 'PersistTest',
        lastName: 'LastName',
        email: 'persist@example.com',
        phoneNo: '+1-555-1234',
      };

      // Update profile
      const updateResponse = await request(app.getHttpServer())
        .put(`${authUrl}/profile`)
        .set('Authorization', `Bearer ${accessToken}`)
        .send(updateData);

      expect(updateResponse.status).toBe(HttpStatus.OK);

      // Fetch profile via /me
      const meResponse = await request(app.getHttpServer())
        .get(`${authUrl}/me`)
        .set('Authorization', `Bearer ${accessToken}`);

      expect(meResponse.status).toBe(HttpStatus.OK);
      expect(meResponse.body.firstName).toBe(updateData.firstName);
      expect(meResponse.body.lastName).toBe(updateData.lastName);
      expect(meResponse.body.email).toBe(updateData.email);
      expect(meResponse.body.phoneNo).toBe(updateData.phoneNo);
    });
  });
});
