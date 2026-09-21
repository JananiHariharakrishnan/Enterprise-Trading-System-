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

export async function POST(req: Request) {
  try {
    const body = await req.json();

    const dto = plainToInstance(RegisterRequest, body);

    const errors = await validate(dto, {
      whitelist: true,
      forbidNonWhitelisted: true,
    });

    if (errors.length > 0) {
      return NextResponse.json(
        {
          errorCode: 'VAL-422',
          message: 'Invalid input',
        },
        { status: 422 },
      );
    }

    // Username must be unique.
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

    // Registration must link to an existing trading account.
    // It must NOT create a trading account.
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

    /*
     * IMPORTANT:
     * createUser currently expects a passwordHash.
     *
     * The credential/password-hashing story owns password hashing.
     * Do not put bcrypt hashing into this route.
     */
    const user = await createUser(
      dto.username,
      dto.password,
      dto.accountId,
    );

    const response = {
      id: user.id,
      username: user.username,
      accountId: user.accountId,
      roles: user.roles,
      createdOn: user.createdOn,
    };

    return NextResponse.json(response, { status: 201 });
  } catch (err) {
    console.error('Registration error:', err);

    return NextResponse.json(
      {
        errorCode: 'VAL-422',
        message: 'Invalid input',
      },
      { status: 422 },
    );
  }
}