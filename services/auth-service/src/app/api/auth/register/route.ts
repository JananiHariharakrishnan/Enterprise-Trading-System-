import 'reflect-metadata';
import { NextResponse } from 'next/server';
import { plainToInstance } from 'class-transformer';
import { validate } from 'class-validator';

import { RegisterRequest } from '../../../../lib/dto/register.dto';

import {
  accountExists,
  findUserByUsername,
  createUser,
} from '../../../../lib/store';

import { createCredential } from '../../../../lib/credentialsStore';
import { logger } from '../../../../lib/logger';

function getTrimmedString(value: unknown): string | null {
  if (typeof value !== 'string') {
    return null;
  }

  const trimmed = value.trim();
  return trimmed ? trimmed : null;
}

function getSafeErrorMessage(err: unknown): string {
  if (err instanceof Error) {
    return getTrimmedString(err.message) ?? 'Registration failed due to server configuration. Check database settings.';
  }

  if (typeof err === 'string') {
    return getTrimmedString(err) ?? 'Registration failed due to server configuration. Check database settings.';
  }

  if (err && typeof err === 'object') {
    const maybeErr = err as {
      message?: unknown;
      detail?: unknown;
      code?: unknown;
      name?: unknown;
    };

    return (
      getTrimmedString(maybeErr.message) ??
      getTrimmedString(maybeErr.detail) ??
      (getTrimmedString(maybeErr.code) ? `Database error (${getTrimmedString(maybeErr.code)})` : null) ??
      (getTrimmedString(maybeErr.name) ? `${getTrimmedString(maybeErr.name)} occurred` : null) ??
      'Registration failed due to server configuration. Check database settings.'
    );
  }

  return 'Registration failed due to server configuration. Check database settings.';
}

export async function POST(req: Request) {
  try {
    // ------------------------------------------------------------
    // 1. Read request body
    // ------------------------------------------------------------
    const body = await req.json();

    // ------------------------------------------------------------
    // 2. Convert request body into DTO
    // ------------------------------------------------------------
    const dto = plainToInstance(RegisterRequest, body);

    // ------------------------------------------------------------
    // 3. Validate request
    // ------------------------------------------------------------
    const errors = await validate(dto, {
      whitelist: true,
      forbidNonWhitelisted: true,
    });

    if (errors.length > 0) {
      /*
       * Log only validation metadata.
       *
       * DO NOT log:
       * - body
       * - dto
       * - password
       * - request object
       */
      const validationDetails = errors.map((error) => ({
        property: error.property,
        constraints: error.constraints,
      }));

      logger.warn('Validation failed', {
        validationDetails,
      });

      /*
       * During development, return validation details so you can
       * immediately see why the request failed.
       *
       * Production keeps the API response generic.
       */
      if (process.env.NODE_ENV === 'development') {
        return NextResponse.json(
          {
            errorCode: 'VAL-422',
            message: 'Invalid input',
            details: validationDetails,
          },
          { status: 422 },
        );
      }

      return NextResponse.json(
        {
          errorCode: 'VAL-422',
          message: 'Invalid input',
        },
        { status: 422 },
      );
    }

    // ------------------------------------------------------------
    // 4. Check username uniqueness
    // ------------------------------------------------------------
    const existing = await findUserByUsername(dto.username);

    if (existing) {
      return NextResponse.json(
        {
          errorCode: 'AUTH-409',
          message: 'Registration failed',
        },
        { status: 409 },
      );
    }

    // ------------------------------------------------------------
    // 5. Verify that the trading account exists
    // ------------------------------------------------------------
    const accountExistsForUser = await accountExists(dto.accountId);

    if (!accountExistsForUser) {
      return NextResponse.json(
        {
          errorCode: 'AUTH-409',
          message: 'Registration failed',
        },
        { status: 409 },
      );
    }

    // ------------------------------------------------------------
    // 6. Create credential
    // ------------------------------------------------------------
    /*
     * createCredential() is responsible for hashing the password.
     *
     * IMPORTANT:
     * dto.password is passed only to the credential layer.
     * It must NEVER be stored directly.
     */
    const credential = await createCredential(
      dto.accountId,
      dto.username,
      dto.password,
    );

    // ------------------------------------------------------------
    // 7. Create user using the generated password hash
    // ------------------------------------------------------------
    const user = await createUser(
      dto.username,
      credential.password_hash,
      dto.accountId,
      dto.first_name,
      dto.last_name,
      dto.email,
      dto.phone_no,
    );

    // ------------------------------------------------------------
    // 8. Build response
    // ------------------------------------------------------------
    /*
     * Never return:
     * - password
     * - password_hash
     * - credential information
     */
    const response = {
      id: user.id,
      username: user.username,
      accountId: user.accountId,
      roles: user.roles,
      createdOn: user.createdOn,
    };

    return NextResponse.json(response, {
      status: 201,
    });
  } catch (err: unknown) {
    // ------------------------------------------------------------
    // 9. Safe error handling
    // ------------------------------------------------------------

    /*
     * Extract only the error message.
     *
     * DO NOT log the complete error object because it can contain
     * request/credential information through nested properties.
     */
    const errorMessage = getSafeErrorMessage(err);

    logger.error('Registration error', {
      message: errorMessage,
    });

    /*
     * During development, expose the actual error message so that
     * database/configuration problems can be diagnosed quickly.
     *
     * In production, return only the contract-safe generic message.
     */
    if (process.env.NODE_ENV === 'development') {
      return NextResponse.json(
        {
          errorCode: 'VAL-422',
          message: errorMessage,
        },
        { status: 422 },
      );
    }

    return NextResponse.json(
      {
        errorCode: 'VAL-422',
        message: 'Invalid input',
      },
      { status: 422 },
    );
  }
}