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

    const body = await req.json();
    const dto = plainToInstance(RegisterRequest, body);
    const errors = await validate(dto, {
      whitelist: true,
      forbidNonWhitelisted: true,
    });

    if (errors.length > 0) {
      const validationDetails = errors.map((error) => ({
        property: error.property,
        constraints: error.constraints,
      }));

      logger.warn('Validation failed', {
        validationDetails,
      });

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

    const credential = await createCredential(
      dto.accountId,
      dto.username,
      dto.password,
    );

    const user = await createUser(
      dto.username,
      credential.password_hash,
      dto.accountId,
      dto.first_name,
      dto.last_name,
      dto.email,
      dto.phone_no,
    );

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

    const errorMessage = getSafeErrorMessage(err);

    logger.error('Registration error', {
      message: errorMessage,
    });

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